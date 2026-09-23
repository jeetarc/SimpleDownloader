package app.jeetarc.simpledownloader;

import android.Manifest
import android.content.SharedPreferences
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.content.res.ColorStateList

import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2

class MainActivity: AppCompatActivity() {
	private lateinit var binding: MainBinding
	private lateinit var navBarAdapter: NavBarAdapter
	private lateinit var selectedItemPref: SharedPreferences
	
	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		binding = MainBinding.inflate(layoutInflater)
		setContentView(binding.root)
		selectedItemPref = getSharedPreferences("selected item", MODE_PRIVATE)
		
        binding.bottomNav.menu.clear()
		binding.bottomNav.menu.add(0, 0, 0, "Input").setIcon(R.drawable.icon_add_link_outline)
		binding.bottomNav.menu.add(0, 1, 1, "Tasks").setIcon(R.drawable.icon_dynamic_feed_outline)
		binding.bottomNav.itemActiveIndicatorColor = ColorStateList.valueOf(Color.TRANSPARENT)
		
		binding.bottomNav.setOnItemSelectedListener { item ->
			binding.viewPagerNav.setCurrentItem(item.itemId, true)
			true
		}
        
		setupViewPager()
		requestNotificationPermission()
	}
	
	private fun setupViewPager() {
		navBarAdapter = NavBarAdapter(this)
		binding.viewPagerNav.adapter = navBarAdapter
		
		binding.viewPagerNav.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
			override fun onPageSelected(position: Int) {
				super.onPageSelected(position)
				binding.bottomNav.menu.findItem(position).isChecked = true
				selectedItemPref.edit().putInt("selectedItem", position).apply()
			}
		})
        
        val selectedItem = selectedItemPref.getInt("selectedItem", 0)
		binding.viewPagerNav.setCurrentItem(selectedItem, false)
        binding.bottomNav.menu.findItem(selectedItem)?.isChecked = true
	}
	
	private fun requestNotificationPermission() {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
			ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1001)
		}
	}
	
	private class NavBarAdapter(activity: MainActivity) : FragmentStateAdapter(activity) {
		override fun getItemCount(): Int = 2
		
		override fun createFragment(position: Int): Fragment {
			return when (position) {
				0 -> DownloadFragment()
				1 -> TaskListFragment()
				else -> throw IllegalStateException("Invalid page position: $position")
			}
		}
	}
}
