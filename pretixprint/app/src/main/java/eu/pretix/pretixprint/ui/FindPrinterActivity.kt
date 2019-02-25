package eu.pretix.pretixprint.ui

import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NavUtils
import androidx.viewpager.widget.ViewPager
import eu.pretix.pretixprint.R
import kotlinx.android.synthetic.main.activity_find.*

class FindPrinterActivity : AppCompatActivity() {
    companion object {
        val EXTRA_TYPE = "TYPE"
    }

    private var type = "ticket"
    private lateinit var viewPager: ViewPager

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_find)
        supportActionBar?.setDisplayShowHomeEnabled(true)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        type = intent.extras.getString(EXTRA_TYPE, "ticket")

        val fragmentAdapter = FindPrinterAdapter(supportFragmentManager, type, this)
        pager.adapter = fragmentAdapter
        viewPager = pager

        tabs.setupWithViewPager(pager)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        val inflater = menuInflater
        inflater.inflate(R.menu.menu_find_printer, menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_save -> {
                val currentFragment : PrinterFragment = supportFragmentManager.findFragmentByTag("android:switcher:" + R.id.pager + ":" + viewPager.currentItem) as PrinterFragment

                if (!currentFragment.validate()) {
                    return true
                }

                currentFragment.savePrefs()
                finish()
                return true
            }
            R.id.action_close -> {
                finish()
                return true
            }
            android.R.id.home -> {
                NavUtils.navigateUpFromSameTask(this)
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }
}