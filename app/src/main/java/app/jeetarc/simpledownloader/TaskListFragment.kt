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

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
		
		private val timestampFormatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())
		private fun formatTimestamp(timestampMillis: Long): String {
			return timestampFormatter.format(Date(timestampMillis))
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
			val itemBinding = ItemLayoutBinding.inflate(LayoutInflater.from(parent.context),parent,false)
			return ViewHolder(itemBinding)
		}
		
		override fun onBindViewHolder(holder: ViewHolder, position: Int) {
			val itemBinding = holder.binding
			val task = getItem(position)
			
			itemBinding.progressBar.progress = task.progress.toInt()
			itemBinding.txtFileName.text = task.fileName
			itemBinding.txtStatus.text = task.status.toString()
			itemBinding.txtTime.text = formatTimestamp(task.createdAt)
			
			when {
				task.isPaused -> {
					itemBinding.iconPauseResume.setImageResource(R.drawable.icon_play_arrow_round)
					itemBinding.txtProgress.text = formatBytesRatio(task)
				}
				
				task.isFailed -> {
					itemBinding.iconPauseResume.setImageResource(R.drawable.icon_refresh_round)
					itemBinding.txtProgress.text = formatBytesRatio(task)
				}
				
				task.isCancelled -> {
					itemBinding.iconPauseResume.setImageResource(R.drawable.icon_block_round)
					itemBinding.txtProgress.text = formatBytesRatio(task)
				}
				
				task.isComplete -> {
					itemBinding.iconPauseResume.setImageResource(R.drawable.icon_done_round)
					itemBinding.txtProgress.text = Formatter.formatBytes(task.downloadedBytes)
				}
				
				task.isActive -> {
					itemBinding.iconPauseResume.setImageResource(R.drawable.icon_pause_round)
					itemBinding.txtProgress.text = "${formatBytesRatio(task)} • " + "${Formatter.formatSpeed(task.speed)} • " + Formatter.formatEta(task.etaMs)
				}
				
				task.isQueued -> {
					itemBinding.iconPauseResume.setImageResource(R.drawable.icon_watch_later_outline)
					itemBinding.txtProgress.text = formatBytesRatio(task)
				}
				
				else -> {
					itemBinding.iconPauseResume.setImageResource(R.drawable.icon_watch_later_outline)
					itemBinding.txtProgress.text = formatBytesRatio(task)
				}
			}
			
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
		
		inner class ViewHolder(val binding: ItemLayoutBinding) : RecyclerView.ViewHolder(binding.root)
	}
	
	private fun formatBytesRatio(task: DownloadTask) : String {
		return Formatter.formatRatio(Formatter.formatBytes(task.downloadedBytes), Formatter.formatBytes(task.totalBytes), " / ");
	}
	
	private fun showToast(msg : String) {
		Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
	}
}
