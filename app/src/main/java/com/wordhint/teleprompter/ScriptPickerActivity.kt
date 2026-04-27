package com.wordhint.teleprompter

import android.app.Activity
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.ListView
import android.widget.TextView
import androidx.activity.ComponentActivity

class ScriptPickerActivity : ComponentActivity() {
    private lateinit var repository: ScriptRepository
    private lateinit var listView: ListView
    private lateinit var emptyText: TextView

    private var scripts: List<Script> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_script_picker)

        repository = ScriptRepository(this)
        listView = findViewById(R.id.scriptList)
        emptyText = findViewById(R.id.emptyText)

        findViewById<Button>(R.id.closeButton).setOnClickListener { finish() }
        findViewById<Button>(R.id.newButton).setOnClickListener {
            setResult(
                Activity.RESULT_OK,
                intent.putExtra(EXTRA_CREATE_NEW, true)
            )
            finish()
        }

        listView.setOnItemClickListener { _, _, position, _ ->
            val selected = scripts.getOrNull(position) ?: return@setOnItemClickListener
            setResult(
                Activity.RESULT_OK,
                intent.putExtra(EXTRA_SCRIPT_ID, selected.id)
            )
            finish()
        }
    }

    override fun onResume() {
        super.onResume()
        refreshList()
    }

    private fun refreshList() {
        scripts = repository.loadScripts()
        emptyText.visibility = if (scripts.isEmpty()) View.VISIBLE else View.GONE
        listView.adapter = ScriptTitleAdapter(scripts)
    }

    private class ScriptTitleAdapter(private val items: List<Script>) : BaseAdapter() {
        override fun getCount(): Int = items.size
        override fun getItem(position: Int): Any = items[position]
        override fun getItemId(position: Int): Long = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val view = convertView ?: LayoutInflater.from(parent.context)
                .inflate(R.layout.item_script_title, parent, false)
            val script = items[position]
            view.findViewById<TextView>(R.id.scriptTitle).text = script.title
            return view
        }
    }

    companion object {
        const val EXTRA_SCRIPT_ID = "selected_script_id"
        const val EXTRA_CREATE_NEW = "create_new"
    }
}
