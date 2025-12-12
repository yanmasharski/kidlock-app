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
    private lateinit var switchBlocking: Switch
    private lateinit var tvMessage: TextView
    private lateinit var rvSections: RecyclerView
    private lateinit var layoutSectionCodes: View
    private lateinit var layoutSectionTime: View
    private lateinit var layoutSectionSecurity: View
    private lateinit var layoutSectionSettings: View

    private lateinit var btnExit: Button

    private lateinit var codeAdapter: CodeAdapter
    private lateinit var sectionAdapter: AdminSectionAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_admin)

        viewModel = ViewModelProvider(this)[AdminViewModel::class.java]

        initViews()
        setupSections()
        setupRecyclerView()
        setupObservers()
        setupClickListeners()
        // Загружаем коды после подписки на изменения
        Log.d("KidLock", "AdminActivity.onCreate() - вызываем loadCodes()")
        viewModel.loadCodes()
    }

    private fun initViews() {
        rvSections = findViewById(R.id.rvSections)
        layoutSectionCodes = findViewById(R.id.layout_section_codes)
        layoutSectionTime = findViewById(R.id.layout_section_time)
        layoutSectionSecurity = findViewById(R.id.layout_section_security)
        layoutSectionSettings = findViewById(R.id.layout_section_settings)

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
        switchBlocking = findViewById(R.id.switchBlocking)
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

    private fun setupSections() {
        val sections = listOf(
            AdminSection(1, getString(R.string.section_codes), true),
            AdminSection(2, getString(R.string.section_time)),
            AdminSection(3, getString(R.string.section_security)),
            AdminSection(4, getString(R.string.section_settings))
        )

        sectionAdapter = AdminSectionAdapter(sections) { section ->
            updateSectionVisibility(section.id)
        }

        rvSections.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        rvSections.adapter = sectionAdapter
        
        // Initial state
        updateSectionVisibility(1)
    }

    private fun updateSectionVisibility(sectionId: Int) {
        layoutSectionCodes.visibility = if (sectionId == 1) View.VISIBLE else View.GONE
        layoutSectionTime.visibility = if (sectionId == 2) View.VISIBLE else View.GONE
        layoutSectionSecurity.visibility = if (sectionId == 3) View.VISIBLE else View.GONE
        layoutSectionSettings.visibility = if (sectionId == 4) View.VISIBLE else View.GONE
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
            tvRemainingTime.text = getString(R.string.remaining_time_admin_format, TimeManager.formatMinutes(this, minutes))
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
            val statusText = if (enabled) getString(R.string.accessibility_enabled) else getString(R.string.accessibility_disabled)
            tvAccessibilityStatus.text = getString(R.string.accessibility_status_format, statusText)
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
            val statusText = if (granted) getString(R.string.usage_stats_granted) else getString(R.string.usage_stats_denied)
            tvUsageStatsStatus.text = getString(R.string.usage_stats_status_format, statusText)
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

        viewModel.isBlockingEnabled.observe(this) { enabled ->
            switchBlocking.isChecked = enabled
        }

        viewModel.canEnableBlocking.observe(this) { canEnable ->
            switchBlocking.isEnabled = canEnable
            // Если блокировка не может быть включена, убеждаемся что она выключена
            if (!canEnable && switchBlocking.isChecked) {
                switchBlocking.isChecked = false
            }
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

        switchBlocking.setOnCheckedChangeListener { _, isChecked ->
            viewModel.setBlockingEnabled(isChecked)
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

