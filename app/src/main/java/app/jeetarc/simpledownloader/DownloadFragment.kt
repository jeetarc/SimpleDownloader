package app.jeetarc.simpledownloader

import android.content.Intent
import android.content.SharedPreferences
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast

import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.viewpager2.widget.ViewPager2

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.jeet.simpledownloader.DownloadRequest
import com.jeet.simpledownloader.FileName

import app.jeetarc.simpledownloader.databinding.DownloadFragmentBinding

class DownloadFragment : Fragment() {
	private lateinit var binding: DownloadFragmentBinding
	private lateinit var prefsFolder: SharedPreferences
	private lateinit var app: App
	
	private var savedTreeUri: Uri? = null
	private var STORAGE_MEDIA_STORE = false
	private var STORAGE_FOLDER_PATH = false
	private var STORAGE_TREE_URI = false
	private var downloadAfterFolderSelection = false
	
	private val folderPickerLauncher = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
		if (uri == null) {
			showToast("Failed to get folder URI, try again.")
			downloadAfterFolderSelection = false
			return@registerForActivityResult
		}
		
		try {
			requireContext().contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
			savedTreeUri = uri
			prefsFolder.edit().putString("saved_tree_uri", uri.toString()).apply()
			
			binding.txtDestination.text = uri.toString()
			showToast("Folder selected successfully!")
			
			if (downloadAfterFolderSelection) {
				downloadAfterFolderSelection = false
				startDownload()
			}
			
		} catch (e: Exception) {
			downloadAfterFolderSelection = false
			showToast(e.message ?: e.toString())
		}
	}
	
	override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
		binding = DownloadFragmentBinding.inflate(inflater, container, false)
		app = requireActivity().application as App
		prefsFolder = requireContext().getSharedPreferences("folder uri", 0)
		
		val savedUri = prefsFolder.getString("saved_tree_uri", "")
		if (!savedUri.isNullOrEmpty()) savedTreeUri = Uri.parse(savedUri)
		setupUi()
        
		if (prefsFolder.getBoolean("storage_media_store", false)) {
			binding.radioMediaStore.isChecked = true
			
		} else if (prefsFolder.getBoolean("storage_tree_uri", false)) {
			binding.radioTreeUri.isChecked = true
			
		} else if (prefsFolder.getBoolean("storage_folder_path", false)) {
			binding.radioFolderPath.isChecked = true
			
		} else {
			binding.radioMediaStore.isChecked = true
		}
		
		return binding.root
	}
	
	private fun setupUi() {
		binding.linearUrl1.background = createRoundedBackground()
		binding.linearUrl2.background = createRoundedBackground()
		binding.linearUrl3.background = createRoundedBackground()
		binding.linearDestination.background = createRoundedBackground()
		
		binding.url1.text = "https://github.com/jeetarc/SimpleDownloader/releases/download/test_files/Dallas-Theme-x-FIFA-World-Cup-26-by-FIFA-from-YouTube-1920x1080px.mp4"
		binding.url2.text = "https://github.com/jeetarc/SimpleDownloader/releases/download/test_files/Wonderful-Gamelan-music-by-FASSounds-from-Pixabay-593303.mp3"
		binding.url3.text = "https://github.com/jeetarc/SimpleDownloader/releases/download/test_files/Cat-sitting-outdoors-in-sunlight-by-Photodailly-from-Pexels-33337218.jpg"
		
		binding.linearUrl1.setOnClickListener {
			binding.txtInputUrl.setText(binding.url1.text.toString())
		}
		
		binding.linearUrl2.setOnClickListener {
			binding.txtInputUrl.setText(binding.url2.text.toString())
		}
		
		binding.linearUrl3.setOnClickListener {
			binding.txtInputUrl.setText(binding.url3.text.toString())
		}
		
		binding.radioMediaStore.setOnCheckedChangeListener { _, checked ->
			if (checked) {
				STORAGE_MEDIA_STORE = true
				STORAGE_TREE_URI = false
				STORAGE_FOLDER_PATH = false
                
				saveStorageMode()
				binding.txtDestination.text = "MediaStore.Downloads.EXTERNAL_CONTENT_URI"
				binding.linearDestination.isEnabled = false
			}
		}
		
		binding.radioTreeUri.setOnCheckedChangeListener { _, checked ->
			if (checked) {
				STORAGE_MEDIA_STORE = false
				STORAGE_TREE_URI = true
				STORAGE_FOLDER_PATH = false
				saveStorageMode()
				
				if (savedTreeUri != null) binding.txtDestination.text = savedTreeUri.toString()
				else binding.txtDestination.text = "Click to select a download folder via SAF"
				
				binding.linearDestination.isEnabled = true
			}
		}
		
		binding.radioFolderPath.setOnCheckedChangeListener { _, checked ->
			if (checked) {
				STORAGE_MEDIA_STORE = false
				STORAGE_TREE_URI = false
				STORAGE_FOLDER_PATH = true
                
				saveStorageMode()
				binding.txtDestination.text = getFolderPath()
				binding.linearDestination.isEnabled = false
			}
		}
		
		binding.linearDestination.setOnClickListener {
			if (STORAGE_TREE_URI) openDocumentTree()
		}
		
		binding.btnStartDownload.setOnClickListener {
			startDownload()
		}
	}
	
	private fun startDownload() {
		val inputUrl = binding.txtInputUrl.text.toString().trim()
		val inputHeaders = binding.txtInputHeaders.text.toString().trim()
		
		if (inputUrl.isEmpty()) {
			showToast("Enter a URL to start download")
			return
		}
		
		val requestBuilder = DownloadRequest.builder().setFileUrl(inputUrl)
		
		if (STORAGE_MEDIA_STORE) {
			if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
				showToast("MediaStore Downloads needs Android 10+. Try to use a different storage mode")
				return
			}
			
			requestBuilder.setOutput(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, FileName.AUTO)
			
		} else if (STORAGE_TREE_URI) {
			if (savedTreeUri == null) {
				showToast("Please select a download folder")
				downloadAfterFolderSelection = true
				openDocumentTree()
				return
			}
			
			requestBuilder.setOutput(savedTreeUri, FileName.AUTO)
			
		} else if (STORAGE_FOLDER_PATH) {
			requestBuilder.setOutput(getFolderPath(), FileName.AUTO)
			
		} else {
			showToast("Please select a storage mode")
			return
		}
		
		if (inputHeaders.isNotEmpty()) {
			try {
				val headers: HashMap<String, String> = Gson().fromJson(inputHeaders, object : TypeToken<HashMap<String, String>>() {}.type)
				requestBuilder.setHeaders(headers)
			} catch (e: Exception) {
				showToast("Unsupported format for headers. Use JSON format")
				return
			}
		}
		
		app.getDownloader().startDownload(requestBuilder.build())
		goToTaskListPage()
	}
	
    private fun openDocumentTree() {
		val uri = Uri.parse("content://com.android.externalstorage.documents/document/primary%3ADownload")
		folderPickerLauncher.launch(uri)
	}
    
	private fun createRoundedBackground(): GradientDrawable {
		val primary = ContextCompat.getColor(requireContext(), R.color.md_theme_primary)
		val faded = ContextCompat.getColor(requireContext(), R.color.md_theme_primary_faded)
		
		return GradientDrawable().apply {
			cornerRadius = 20f
			setStroke(2, primary)
			setColor(faded)
		}
	}
	
	private fun saveStorageMode() {
		prefsFolder.edit()
		.putBoolean("storage_media_store", STORAGE_MEDIA_STORE)
		.putBoolean("storage_tree_uri", STORAGE_TREE_URI)
		.putBoolean("storage_folder_path", STORAGE_FOLDER_PATH)
		.apply()
	}
	
	private fun getFolderPath(): String {
		return requireContext().getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)?.absolutePath ?: requireContext().filesDir.absolutePath
	}
	
	fun goToTaskListPage() {
		val viewPager = requireActivity().findViewById<ViewPager2>(R.id.viewPagerNav)
		viewPager.setCurrentItem(1, true)
	}
	
	private fun showToast(message: String) {
		Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
	}
}
