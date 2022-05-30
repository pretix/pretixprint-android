package eu.pretix.pretixprint.ui

import android.os.Bundle
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import eu.pretix.pretixprint.R
import eu.pretix.pretixprint.databinding.ActivityMaintenanceBinding

class MaintenanceActivity : AppCompatActivity() {
    companion object {
        const val EXTRA_TYPE = "type"
    }

    private lateinit var binding: ActivityMaintenanceBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val printerType: String? = intent.extras?.get(EXTRA_TYPE) as String?
        if (printerType == null) {
            finish()
            return
        }

        binding = ActivityMaintenanceBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)

        val typeRef = resources.getIdentifier("settings_label_${printerType}printer", "string", packageName)
        supportActionBar?.title = getString(R.string.title_activity_maintenance_type, getString(typeRef))

        val fragmentTransaction = supportFragmentManager.beginTransaction()
        val fragment = MaintenanceFragment.newInstance(printerType)
        fragmentTransaction.replace(R.id.frame, fragment)
        fragmentTransaction.commit()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
        }
        return super.onOptionsItemSelected(item)
    }
}