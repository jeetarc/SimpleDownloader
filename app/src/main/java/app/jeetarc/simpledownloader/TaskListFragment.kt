package app.jeetarc.simpledownloader

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast

import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView

import com.jeet.simpledownloader.DownloadTask
import com.jeet.simpledownloader.Status
import com.jeet.simpledownloader.TaskListObserver
import com.jeet.simpledownloader.util.Formatter

import app.jeetarc.simpledownloader.databinding.ItemLayoutBinding
import app.jeetarc.simpledownloader.databinding.TaskListFragmentBinding

class TaskListFragment : Fragment() {
	private lateinit var binding: TaskListFragmentBinding
	private lateinit var adapter: RecyclerViewAdapter
	private lateinit var observer: TaskListObserver
	private lateinit var app: App
	private lateinit var diffUtilCallback: DiffUtil.ItemCallback<DownloadTask>
	
	override fun onCreateView(inflater: LayoutInflater,container: ViewGroup?, savedInstanceState: Bundle?): View {
		binding = TaskListFragmentBinding.inflate(inflater, container,false)
		app = requireActivity().application as App
		
		setupDiffUtilCallback()
		setupRecyclerAdapter()
		setupTaskObserver()
		
		return binding.root
	}
	
	private fun setupRecyclerAdapter() {
		binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
		adapter = RecyclerViewAdapter()
		binding.recyclerView.adapter = adapter
	}
	
	private fun setupTaskObserver() {
		observer = object : TaskListObserver {
			override fun onTasksChanged(size: Int, tasks: List<DownloadTask>) {
				adapter.submitList(tasks)
			}
			
			override fun onTaskUpdated(id: Long, task: DownloadTask) {
				adapter.updateItem(id)
			}
		}
		
		app.getDownloader().addObserver(observer)
	}
	
	private fun setupDiffUtilCallback() {
		diffUtilCallback = object : DiffUtil.ItemCallback<DownloadTask>() {
			override fun areItemsTheSame(oldItem: DownloadTask, newItem: DownloadTask): Boolean {
				return oldItem.id == newItem.id
			}
			
			override fun areContentsTheSame(oldItem: DownloadTask, newItem: DownloadTask): Boolean {
				return true // 'true' because onTaskUpdated already updateds each task items
			}
		}
	}
	
	override fun onDestroyView() {
		app.getDownloader().removeObserver(observer)
		super.onDestroyView()
	}
	
	inner class RecyclerViewAdapter : ListAdapter<DownloadTask, RecyclerViewAdapter.ViewHolder>(diffUtilCallback) {
		private val PAYLOAD_TASK_UPDATE = Any()
		
		init {
			setHasStableIds(true)
		}
		
		override fun getItemId(position: Int): Long {
			return getItem(position).id
		}
		
		fun updateItem(id: Long) {
			for (position in 0 until itemCount) {
				if (getItem(position).id == id) {
					notifyItemChanged(position, PAYLOAD_TASK_UPDATE)
					return
				}
			}
		}
		
		override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
			val itemBinding = ItemLayoutBinding.inflate(LayoutInflater.from(parent.context), parent, false)
			return ViewHolder(itemBinding)
		}
		
		private fun updateProgressViews(binding: ItemLayoutBinding, task: DownloadTask) {
			
			// Keeping file name here because this can resolve leater from the server (while connecting) for FileName.AUTO / FileName.TIME_BASED,
			// if the URL doesn't include a proper file name or extension.
			binding.txtFileName.text = task.fileName 
			
			binding.progressBar.progress = task.progress
			binding.txtStatus.text = task.status.toString()
			
			if (task.isPaused) {
				binding.iconPauseResume.setImageResource(R.drawable.icon_play_arrow_round)
				binding.txtProgress.text = formatBytesRatio(task)
				
			} else if (task.isFailed) {
				binding.iconPauseResume.setImageResource(R.drawable.icon_refresh_round)
				binding.txtProgress.text = formatBytesRatio(task)
				
			} else if (task.isCancelled) {
				binding.iconPauseResume.setImageResource(R.drawable.icon_block_round)
				binding.txtProgress.text = formatBytesRatio(task)
				
			} else if (task.isComplete) {
				binding.iconPauseResume.setImageResource(R.drawable.icon_done_round)
				binding.txtProgress.text = Formatter.formatBytes(task.downloadedBytes)
				
			} else if (task.isActive) {
				binding.iconPauseResume.setImageResource(R.drawable.icon_pause_round)
				binding.txtProgress.text = "${formatBytesRatio(task)} • " + Formatter.formatSpeed(task.speed) + " • " + Formatter.formatEta(task.etaMs)
				
			} else if (task.isQueued) {
				binding.iconPauseResume.setImageResource(R.drawable.icon_watch_later_outline)
				binding.txtProgress.text = formatBytesRatio(task)
				
			} else {
				binding.iconPauseResume.setImageResource(R.drawable.icon_watch_later_outline)
				binding.txtProgress.text = formatBytesRatio(task)
			}
		}
		
		override fun onBindViewHolder(holder: ViewHolder, position: Int) {
			val itemBinding = holder.binding
			val task = getItem(position)
			
			itemBinding.txtTime.text = Formatter.formatTime(task.createdAt, "yyyy-MM-dd HH:mm:ss.SSS")
			updateProgressViews(itemBinding, task)
			
			
			itemBinding.iconPauseResume.setOnClickListener {
				val pos = holder.bindingAdapterPosition
				if (pos == RecyclerView.NO_POSITION) return@setOnClickListener
				val task = getItem(pos)
				
				if (task.canResume()) {
					task.resume()
					
				} else if (task.canRetry()) {
					task.retry()
					
				} else if (task.canPause()) {
					task.pause()
				}
				
			}
			
			itemBinding.btnOpen.setOnClickListener {
				val pos = holder.bindingAdapterPosition
				if (pos == RecyclerView.NO_POSITION) return@setOnClickListener
				val task = getItem(pos)
				if (task.outputUri == null) return@setOnClickListener
				
				val contentView = Intent(Intent.ACTION_VIEW).apply {
					setDataAndType(task.outputUri, task.mimeType)
					addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
				}
				
				try {
					startActivity(contentView)
				} catch (e: ActivityNotFoundException) {
					showToast("No app available to open this file")
				}
			}
			
			itemBinding.btnLock.setOnClickListener {
				val pos = holder.bindingAdapterPosition
				if (pos == RecyclerView.NO_POSITION) return@setOnClickListener
				val task = getItem(pos)
				
				if (task.isLockedInQueue) {
					task.setLockedInQueue(false)
					itemBinding.iconPauseResume.setImageResource(R.drawable.icon_watch_later_outline)
					
				} else {
					task.setLockedInQueue(true)
					itemBinding.iconPauseResume.setImageResource(R.drawable.icon_lock_outline)
				}
			}
			
			itemBinding.btnCancel.setOnClickListener {
				val pos = holder.bindingAdapterPosition
				if (pos == RecyclerView.NO_POSITION) return@setOnClickListener
				val task = getItem(pos)
				task.cancel()
			}
			
			itemBinding.btnRemove.setOnClickListener {
				val pos = holder.bindingAdapterPosition
				if (pos == RecyclerView.NO_POSITION) return@setOnClickListener
				val task = getItem(pos)
				task.remove()
			}
		}
		
		override fun onBindViewHolder(holder: ViewHolder, position: Int, payloads: MutableList<Any>) {
			if (payloads.contains(PAYLOAD_TASK_UPDATE)) {
				updateProgressViews(holder.binding, getItem(position))
				return
			}
			
			super.onBindViewHolder(holder, position, payloads)
		}
		
		inner class ViewHolder(val binding: ItemLayoutBinding) : RecyclerView.ViewHolder(binding.root)
	}
	
	
	private fun formatBytesRatio(task: DownloadTask) : String {
		return Formatter.formatRatio(Formatter.formatBytes(task.downloadedBytes), Formatter.formatBytes(task.totalBytes), " / ");
	}
	
	private fun showToast(msg : String) {
		Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
	}
}
