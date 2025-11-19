package uk.telegramgames.kidlock

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.Switch
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class AdminActivity : AppCompatActivity() {
    private lateinit var viewModel: AdminViewModel
    private lateinit var tvRemainingTime: TextView
    private lateinit var etDailyLimit: EditText
    private lateinit var btnSetLimit: Button
    private lateinit var etCodeCount: EditText
    private lateinit var etMinutesPerCode: EditText
    private lateinit var btnGenerateCodes: Button
    private lateinit var rvCodes: RecyclerView
    private lateinit var etNewPin: EditText
    private lateinit var btnChangePin: Button
    private lateinit var btnUnlock: Button
    private lateinit var tvAccessibilityStatus: TextView
    private lateinit var tvAccessibilityInstructions: TextView
    private lateinit var btnOpenAccessibilitySettings: Button
    private lateinit var tvUsageStatsStatus: TextView
    private lateinit var tvUsageStatsInstructions: TextView
    private lateinit var btnOpenUsageStatsSettings: Button
    private lateinit var switchAutostart: Switch
    private lateinit var tvMessage: TextView
    private lateinit var btnExit: Button

    private lateinit var codeAdapter: CodeAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_admin)

        viewModel = ViewModelProvider(this)[AdminViewModel::class.java]

        initViews()
        setupRecyclerView()
        setupObservers()
        setupClickListeners()
        // Загружаем коды после подписки на изменения
        Log.d("KidLock", "AdminActivity.onCreate() - вызываем loadCodes()")
        viewModel.loadCodes()
    }

    private fun initViews() {
        tvRemainingTime = findViewById(R.id.tvRemainingTime)
        etDailyLimit = findViewById(R.id.etDailyLimit)
        btnSetLimit = findViewById(R.id.btnSetLimit)
        etCodeCount = findViewById(R.id.etCodeCount)
        etMinutesPerCode = findViewById(R.id.etMinutesPerCode)
        btnGenerateCodes = findViewById(R.id.btnGenerateCodes)
        rvCodes = findViewById(R.id.rvCodes)
        etNewPin = findViewById(R.id.etNewPin)
        btnChangePin = findViewById(R.id.btnChangePin)
        btnUnlock = findViewById(R.id.btnUnlock)
        tvAccessibilityStatus = findViewById(R.id.tvAccessibilityStatus)
        tvAccessibilityInstructions = findViewById(R.id.tvAccessibilityInstructions)
        btnOpenAccessibilitySettings = findViewById(R.id.btnOpenAccessibilitySettings)
        tvUsageStatsStatus = findViewById(R.id.tvUsageStatsStatus)
        tvUsageStatsInstructions = findViewById(R.id.tvUsageStatsInstructions)
        btnOpenUsageStatsSettings = findViewById(R.id.btnOpenUsageStatsSettings)
        switchAutostart = findViewById(R.id.switchAutostart)
        tvMessage = findViewById(R.id.tvMessage)
        btnExit = findViewById(R.id.btnExit)
        
        // Настройка показа клавиатуры для EditText полей
        setupEditTextKeyboard(etDailyLimit)
        setupEditTextKeyboard(etCodeCount)
        setupEditTextKeyboard(etMinutesPerCode)
        setupEditTextKeyboard(etNewPin)
    }
    
    private fun setupEditTextKeyboard(editText: EditText) {
        editText.setOnFocusChangeListener { view, hasFocus ->
            if (hasFocus) {
                val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT)
            }
        }
        editText.setOnClickListener {
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(it, InputMethodManager.SHOW_IMPLICIT)
        }
    }

    private fun setupRecyclerView() {
        Log.d("KidLock", "AdminActivity.setupRecyclerView() - инициализация RecyclerView")
        codeAdapter = CodeAdapter(emptyList()) { code ->
            viewModel.deleteCode(code)
        }
        rvCodes.layoutManager = LinearLayoutManager(this)
        rvCodes.adapter = codeAdapter
        Log.d("KidLock", "AdminActivity.setupRecyclerView() - RecyclerView настроен, adapter установлен, начальный getItemCount=${codeAdapter.itemCount}")
    }

    private fun setupObservers() {
        viewModel.dailyLimitMinutes.observe(this) { minutes ->
            etDailyLimit.setText(minutes.toString())
        }

        viewModel.remainingTimeMinutes.observe(this) { minutes ->
            tvRemainingTime.text = "Оставшееся время: ${TimeManager.formatMinutes(minutes)}"
        }

        viewModel.codes.observe(this) { codes ->
            Log.d("KidLock", "AdminActivity.codes observer - получено ${codes.size} кодов: ${codes.map { "${it.value}(${it.addedTimeMinutes}мин, used=${it.isUsed})" }}")
            codeAdapter.updateCodes(codes)
            Log.d("KidLock", "AdminActivity.codes observer - адаптер обновлен, getItemCount=${codeAdapter.itemCount}")
            // Прокручиваем к началу списка после обновления
            if (codes.isNotEmpty()) {
                rvCodes.scrollToPosition(0)
                Log.d("KidLock", "AdminActivity.codes observer - прокрутка к началу списка")
            }
        }

        viewModel.isAccessibilityServiceEnabled.observe(this) { enabled ->
            tvAccessibilityStatus.text = "Accessibility Service: ${if (enabled) "Включен" else "Выключен"}"
            tvAccessibilityStatus.setTextColor(
                if (enabled) {
                    getColor(android.R.color.holo_green_light)
                } else {
                    getColor(android.R.color.holo_red_light)
                }
            )
            // Показываем инструкции, если служба не включена
            tvAccessibilityInstructions.visibility = if (enabled) View.GONE else View.VISIBLE
        }

        viewModel.isUsageStatsPermissionGranted.observe(this) { granted ->
            tvUsageStatsStatus.text = "Usage Stats: ${if (granted) "Разрешено" else "Не разрешено"}"
            tvUsageStatsStatus.setTextColor(
                if (granted) {
                    getColor(android.R.color.holo_green_light)
                } else {
                    getColor(android.R.color.holo_red_light)
                }
            )
            // Показываем инструкции, если разрешение не предоставлено
            tvUsageStatsInstructions.visibility = if (granted) View.GONE else View.VISIBLE
        }

        viewModel.isAutostartEnabled.observe(this) { enabled ->
            switchAutostart.isChecked = enabled
        }

        viewModel.message.observe(this) { message ->
            tvMessage.text = message ?: ""
            tvMessage.visibility = if (message.isNullOrEmpty()) View.GONE else View.VISIBLE
        }
    }

    private fun setupClickListeners() {
        btnSetLimit.setOnClickListener {
            val minutes = etDailyLimit.text.toString().toIntOrNull() ?: 0
            viewModel.setDailyLimitMinutes(minutes)
        }

        btnGenerateCodes.setOnClickListener {
            val count = etCodeCount.text.toString().toIntOrNull() ?: 0
            val minutes = etMinutesPerCode.text.toString().toIntOrNull() ?: 30
            Log.d("KidLock", "AdminActivity - кнопка генерации нажата: count=$count, minutes=$minutes")
            viewModel.generateCodes(count, minutes)
            etCodeCount.setText("")
        }

        btnChangePin.setOnClickListener {
            val newPin = etNewPin.text.toString()
            viewModel.changePin(newPin)
            etNewPin.setText("")
        }

        btnUnlock.setOnClickListener {
            viewModel.unlock()
        }

        btnOpenAccessibilitySettings.setOnClickListener {
            // Показываем инструкции перед открытием настроек
            tvAccessibilityInstructions.visibility = View.VISIBLE
            viewModel.openAccessibilitySettings()
        }

        btnOpenUsageStatsSettings.setOnClickListener {
            // Показываем инструкции перед открытием настроек
            tvUsageStatsInstructions.visibility = View.VISIBLE
            viewModel.openUsageStatsSettings()
        }

        switchAutostart.setOnCheckedChangeListener { _, isChecked ->
            viewModel.setAutostartEnabled(isChecked)
        }

        btnExit.setOnClickListener {
            finish()
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.checkPermissions()
        viewModel.updateRemainingTime()
        viewModel.loadCodes()
    }
}

