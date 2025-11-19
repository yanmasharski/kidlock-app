package uk.telegramgames.kidlock

import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider

class PinInputFragment : Fragment() {
    private lateinit var viewModel: PinInputViewModel
    private lateinit var tvPinInput: TextView
    private lateinit var tvErrorMessage: TextView

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_pin_input, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel = ViewModelProvider(this)[PinInputViewModel::class.java]

        tvPinInput = view.findViewById(R.id.tvPinInput)
        tvErrorMessage = view.findViewById(R.id.tvErrorMessage)

        // Настройка обработки нажатий клавиш
        view.isFocusableInTouchMode = true
        view.requestFocus()
        view.setOnKeyListener { _, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN) {
                when (keyCode) {
                    KeyEvent.KEYCODE_0 -> { addDigit('0'); true }
                    KeyEvent.KEYCODE_1 -> { addDigit('1'); true }
                    KeyEvent.KEYCODE_2 -> { addDigit('2'); true }
                    KeyEvent.KEYCODE_3 -> { addDigit('3'); true }
                    KeyEvent.KEYCODE_4 -> { addDigit('4'); true }
                    KeyEvent.KEYCODE_5 -> { addDigit('5'); true }
                    KeyEvent.KEYCODE_6 -> { addDigit('6'); true }
                    KeyEvent.KEYCODE_7 -> { addDigit('7'); true }
                    KeyEvent.KEYCODE_8 -> { addDigit('8'); true }
                    KeyEvent.KEYCODE_9 -> { addDigit('9'); true }
                    KeyEvent.KEYCODE_DEL -> { removeDigit(); true }
                    KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_DPAD_CENTER -> { verifyPin(); true }
                    else -> false
                }
            } else {
                false
            }
        }

        setupKeyboard(view)
        setupObservers()
    }

    private fun setupKeyboard(view: View) {
        for (i in 0..9) {
            val buttonId = resources.getIdentifier("btn$i", "id", requireContext().packageName)
            val button = view.findViewById<Button?>(buttonId)
            button?.apply {
                isFocusable = true
                isFocusableInTouchMode = true
                setOnClickListener { addDigit(i.toString()[0]) }
            }
        }

        view.findViewById<Button>(R.id.btnBack)?.apply {
            isFocusable = true
            isFocusableInTouchMode = true
            setOnClickListener { removeDigit() }
        }
        view.findViewById<Button>(R.id.btnEnter)?.apply {
            isFocusable = true
            isFocusableInTouchMode = true
            setOnClickListener { verifyPin() }
        }
        
        // Устанавливаем фокус на первую кнопку
        view.post {
            view.findViewById<Button>(R.id.btn1)?.requestFocus()
        }
    }

    private fun addDigit(digit: Char) {
        viewModel.addDigit(digit)
    }

    private fun removeDigit() {
        viewModel.removeDigit()
    }

    private fun verifyPin() {
        viewModel.verifyPin()
        // Переход обрабатывается через observer isPinCorrect
    }

    private fun setupObservers() {
        viewModel.pinInput.observe(viewLifecycleOwner) { pin ->
            displayPin(pin)
        }

        viewModel.errorMessage.observe(viewLifecycleOwner) { error ->
            tvErrorMessage.text = error ?: ""
            tvErrorMessage.visibility = if (error.isNullOrEmpty()) View.GONE else View.VISIBLE
        }

        viewModel.isPinCorrect.observe(viewLifecycleOwner) { isCorrect ->
            if (isCorrect) {
                // PIN верный, переходим к админ-панели
                startActivity(Intent(requireContext(), AdminActivity::class.java))
                requireActivity().finish()
            }
        }
    }

    private fun displayPin(pin: String) {
        val display = pin.padEnd(4, '-')
        tvPinInput.text = display
    }
    
    override fun onResume() {
        super.onResume()
        // Устанавливаем фокус на view для обработки нажатий клавиш
        view?.requestFocus()
    }
}

