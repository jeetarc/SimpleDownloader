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
import androidx.fragment.app.FragmentStatePagerAdapter
import androidx.fragment.app.FragmentManager
import androidx.viewpager.widget.ViewPager

import app.jeetarc.simpledownloader.databinding.MainBinding

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
		navBarAdapter = NavBarAdapter(supportFragmentManager)
		binding.viewPagerNav.adapter = navBarAdapter
		
		binding.viewPagerNav.addOnPageChangeListener(object : ViewPager.OnPageChangeListener {
			override fun onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int) {}
			
			override fun onPageSelected(position: Int) {
				binding.bottomNav.menu.findItem(position).isChecked = true
				selectedItemPref.edit().putInt("selectedItem", position).apply()
			}
			
			override fun onPageScrollStateChanged(state: Int) {}
		})
		
		val selectedItem = selectedItemPref.getInt("selectedItem", 0)
		binding.viewPagerNav.currentItem = selectedItem
		binding.bottomNav.menu.findItem(selectedItem)?.isChecked = true
	}
	
	private fun requestNotificationPermission() {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
			ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1001)
		}
	}
	
	private class NavBarAdapter(fragmentManager: FragmentManager) : FragmentStatePagerAdapter(fragmentManager) {
		override fun getCount(): Int = 2
		
		override fun getItem(position: Int): Fragment {
			return when (position) {
				0 -> DownloadFragment()
				1 -> TaskListFragment()
				else -> throw IllegalStateException("Invalid page position: $position")
			}
		}
	}
}
