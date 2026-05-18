package com.jeet.simpledownloader;

/*
 * Copyright (c) 2026 Jeet Jati, under jeetarc.
 *
 * This source code is part of SimpleDownloader.
 */
 
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.os.Build;
import java.util.ArrayList;
import java.util.List;

final class NetworkManager {
	static final int NETWORK_TYPE_NONE = -1;
	static final int NETWORK_TYPE_UNKNOWN = 0;
	static final int NETWORK_TYPE_WIFI = 1;
	static final int NETWORK_TYPE_CELLULAR = 2;
	static final int NETWORK_TYPE_ETHERNET = 3;
	static final int NETWORK_TYPE_BLUETOOTH = 4;
	static final int NETWORK_TYPE_VPN = 5;
	static final int NETWORK_TYPE_USB = 6;
	static final int NETWORK_TYPE_ROAMING = 7;
	
	private int downKbps = 0;
	private int lastDownKbps = 0;
	private boolean networkAvailable = false;
	private int networkType = NETWORK_TYPE_NONE;
	private int lastHandledNetworkType = NETWORK_TYPE_NONE;
	private boolean retryOnNetworkGain = true;
	private ConnectivityManager connectivityManager;
	private ConnectivityManager.NetworkCallback networkCallback;
	private final List<DownloadTask> waitingForPreferredNetwork = new ArrayList<>();
	
	NetworkManager() {}
	
	void register(Context appContext) {
		if (appContext == null) return;
		if (connectivityManager == null) connectivityManager = (ConnectivityManager) appContext.getSystemService(Context.CONNECTIVITY_SERVICE);
		registerCallback();
	}
	
	boolean isNetworkAvailable() {
		if (connectivityManager == null) return networkAvailable;
		
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
			Network network = connectivityManager.getActiveNetwork();
			if (network == null) return false;
			NetworkCapabilities caps = connectivityManager.getNetworkCapabilities(network);
			if (caps == null) return false;
		}
		
		android.net.NetworkInfo info = connectivityManager.getActiveNetworkInfo();
		return info != null && info.isConnected();
	}
	
	int getNetworkType() {
		if (connectivityManager == null) return networkType;
		
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
			Network network = connectivityManager.getActiveNetwork();
			if (network == null) return NETWORK_TYPE_NONE;
			NetworkCapabilities caps = connectivityManager.getNetworkCapabilities(network);
			if (caps == null) return NETWORK_TYPE_UNKNOWN;
			if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) return NETWORK_TYPE_WIFI;
			if (Build.VERSION.SDK_INT >= 26 && caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI_AWARE)) return NETWORK_TYPE_WIFI;
			
			if (caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
				if (!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_ROAMING)) return NETWORK_TYPE_ROAMING;
				return NETWORK_TYPE_CELLULAR;
			}
			
			if (caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) return NETWORK_TYPE_ETHERNET;
			if (caps.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH)) return NETWORK_TYPE_BLUETOOTH;
			if (caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) return NETWORK_TYPE_VPN;
			if (Build.VERSION.SDK_INT >= 31 && caps.hasTransport(NetworkCapabilities.TRANSPORT_USB)) return NETWORK_TYPE_USB;
			return NETWORK_TYPE_UNKNOWN;
		}
		
		android.net.NetworkInfo info = connectivityManager.getActiveNetworkInfo();
		if (info == null || !info.isConnected()) return NETWORK_TYPE_NONE;
		
		int type = info.getType();
		if (type == ConnectivityManager.TYPE_WIFI) return NETWORK_TYPE_WIFI;
		if (type == ConnectivityManager.TYPE_MOBILE) return NETWORK_TYPE_CELLULAR;
		if (type == ConnectivityManager.TYPE_ETHERNET) return NETWORK_TYPE_ETHERNET;
		if (type == ConnectivityManager.TYPE_BLUETOOTH) return NETWORK_TYPE_BLUETOOTH;
		return NETWORK_TYPE_UNKNOWN;
	}
	
	void setRetryOnNetworkGain(boolean enable) {
		retryOnNetworkGain = enable;
	}
	
	boolean isRetryOnNetworkGainEnabled() {
		return retryOnNetworkGain;
	}
	
	boolean shouldWaitForPreferredNetwork(DownloadTask task) {
		if (task == null) return false;
		if (!isNetworkAvailable()) return false;
		
		return task.mWifiOnly && getNetworkType() != NETWORK_TYPE_WIFI;
	}
	
	boolean isPreferredNetworkUnavailable(DownloadTask task) {
		return shouldWaitForPreferredNetwork(task);
	}
	
	void moveToWaitingForNetwork(DownloadTask task) {
		if (task == null) return;
		task.mSpeed = 0;
		task.mEta = -1;
		task.setStatus(Status.WAITING_FOR_NETWORK);
		WaitingDecision decision;
		
		android.util.Log.d("NET_WAIT", 
		"WAITING taskObj=" + System.identityHashCode(task) 
		+ " id=" + task.mId 
		+ " statusBefore=" + task.status 
		+ " canRunNow=" + canRunNow(task)
		+ " networkAvailable=" + isNetworkAvailable()
		+ " networkType=" + getNetworkType());
		
		synchronized (SimpleDownloader.class) {
			decision = decideWaitingForNetwork(task);
			
			if (decision.addToPreferredWaiting) {
				addWaitingForPreferredNetwork(task);
			} else removeWaitingForPreferredNetwork(task);
			
			SimpleDownloader.sortTaskListLocked();
		}
		
		ListenerDispatcher.onWaitingForNetwork(task);
		SlotHandler.finishTask(task, false, decision.releaseSlot);
	}
	
	boolean addWaitingForPreferredNetwork(DownloadTask task) {
		if (task == null) return false;
		if (!waitingForPreferredNetwork.contains(task)) {
			waitingForPreferredNetwork.add(task);
			return true;
		}
		return false;
	}
	
	boolean removeWaitingForPreferredNetwork(DownloadTask task) {
		return task != null && waitingForPreferredNetwork.remove(task);
	}
	
	boolean isWaitingForPreferredNetwork(DownloadTask task) {
		return task != null && waitingForPreferredNetwork.contains(task);
	}
	
	List<DownloadTask> getWaitingForPreferredNetwork() {
		return waitingForPreferredNetwork;
	}
	
	boolean hasWaitingTasks() {
		return !waitingForPreferredNetwork.isEmpty();
	}
	
	void release() {
		if (!SimpleDownloader.sRegistry.isEmpty() || !waitingForPreferredNetwork.isEmpty() || SlotHandler.hasWork()) return;
		if (connectivityManager != null && networkCallback != null) {
			try {
				connectivityManager.unregisterNetworkCallback(networkCallback);
			} catch (Exception ignored) {}
			networkCallback = null;
		}
	}
	
	private void registerCallback() {
		if (connectivityManager == null || networkCallback != null) return;
		networkCallback = new ConnectivityManager.NetworkCallback() {
			@Override
			public void onAvailable(Network network) {
				networkAvailable = true;
			}
			
			@Override
			public void onLost(Network network) {
				networkAvailable = false;
				networkType = NETWORK_TYPE_NONE;
				lastHandledNetworkType = NETWORK_TYPE_NONE;
				downKbps = 0;
				lastDownKbps = 0;
			}
			
			@Override
			public void onCapabilitiesChanged(Network network, NetworkCapabilities caps) {
				if (caps != null) {
					boolean isWifi = caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI);
					if (Build.VERSION.SDK_INT >= 26) isWifi = isWifi || caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI_AWARE);
					if (isWifi) {
						networkType = NETWORK_TYPE_WIFI;
					} else if (caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
						networkType = NETWORK_TYPE_CELLULAR;
						if (!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_ROAMING)) networkType = NETWORK_TYPE_ROAMING;
					} else if (caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) {
						networkType = NETWORK_TYPE_ETHERNET;
					} else if (caps.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH)) {
						networkType = NETWORK_TYPE_BLUETOOTH;
					} else if (caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) {
						networkType = NETWORK_TYPE_VPN;
					} else if (Build.VERSION.SDK_INT >= 31 && caps.hasTransport(NetworkCapabilities.TRANSPORT_USB)) {
						networkType = NETWORK_TYPE_USB;
					} else networkType = NETWORK_TYPE_UNKNOWN;
				}
				
				int newDownKbps = caps != null ? caps.getLinkDownstreamBandwidthKbps() : 0;
				boolean speedChanged = isSpeedChanged(lastDownKbps, newDownKbps);
				downKbps = newDownKbps;
				if (newDownKbps > 0) lastDownKbps = newDownKbps;
				if (networkType == lastHandledNetworkType && !speedChanged) return;
				if (networkType != NETWORK_TYPE_UNKNOWN) lastHandledNetworkType = networkType;
				
				for (DownloadTask task : new ArrayList<>(SimpleDownloader.sRegistry.values())) {
					if (task.status == Status.PAUSED) continue;
					
					if (speedChanged && (task.status == Status.DOWNLOADING || task.status == Status.CONNECTING)) {
						task.mRefreshRequested = true;
						task.cancelRunningCall();
					}
					
					if (task.mWifiOnly && getNetworkType() != NETWORK_TYPE_WIFI) {
						if (task.status == Status.DOWNLOADING || task.status == Status.CONNECTING) {
							task.mNetworkPaused = true;
							task.cancelRunningCall();
						}
					} else if (task.status == Status.WAITING_FOR_NETWORK && retryOnNetworkGain && !waitingForPreferredNetwork.contains(task)) {
						task.resumeOccupiedWaiting();
					}
				}
				
				synchronized (SimpleDownloader.class) {
					SlotHandler.dispatchReadyTasks();
				}
			}
		};
		
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
			connectivityManager.registerDefaultNetworkCallback(networkCallback);
		} else {
			NetworkRequest request = new NetworkRequest.Builder().build();
			connectivityManager.registerNetworkCallback(request, networkCallback);
		}
	}
	
	private boolean isSpeedChanged(int oldKbps, int newKbps) {
		if (oldKbps <= 0 || newKbps <= 0) return false;
		int diff = Math.abs(newKbps - oldKbps);
		return diff * 100 >= oldKbps * 50;
	}
	
	static final class WaitingDecision {
		final boolean addToPreferredWaiting;
		final boolean releaseSlot;
		
		WaitingDecision(boolean addToPreferredWaiting, boolean releaseSlot) {
			this.addToPreferredWaiting = addToPreferredWaiting;
			this.releaseSlot = releaseSlot;
		}
	}
	
	WaitingDecision decideWaitingForNetwork(DownloadTask task) {
		if (task == null) return new WaitingDecision(false, false);
		if (!isNetworkAvailable()) return new WaitingDecision(false, false);
		
		if (task.mWifiOnly && getNetworkType() != NETWORK_TYPE_WIFI) {
			boolean hasRunnableQueuedTask = SlotHandler.hasRunnableQueuedTaskLocked();
			if (hasRunnableQueuedTask) return new WaitingDecision(true, true);
			return new WaitingDecision(false, false);
		}
		return new WaitingDecision(false, false);
	}
	
	boolean canRunNow(DownloadTask task) {
		if (task == null) return false;
		if (!isNetworkAvailable()) return false;
		if (task.mWifiOnly && getNetworkType() != SimpleDownloader.NETWORK_TYPE_WIFI) return false;
		return true;
	}
}
