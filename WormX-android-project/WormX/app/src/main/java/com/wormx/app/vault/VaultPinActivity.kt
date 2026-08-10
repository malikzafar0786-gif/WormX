package com.wormx.app.vault

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import com.wormx.app.databinding.ActivityVaultPinBinding

/**
 * Gate screen for the vault: PIN keypad with an optional biometric shortcut.
 * Kept as a separate lock screen from the app's main PIN (if any) so hiding
 * files behind this doesn't depend on the OS-level screen lock alone.
 */
class VaultPinActivity : AppCompatActivity() {

    private lateinit var binding: ActivityVaultPinBinding
    private lateinit var crypto: VaultCryptoManager
    private val enteredPin = StringBuilder()
    private var lockedOut = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityVaultPinBinding.inflate(layoutInflater)
        setContentView(binding.root)
        crypto = VaultCryptoManager(this)

        setupKeypad()
        offerBiometricIfAvailable()
    }

    private fun setupKeypad() {
        val digitButtons = listOf(
            binding.key0, binding.key1, binding.key2, binding.key3, binding.key4,
            binding.key5, binding.key6, binding.key7, binding.key8, binding.key9
        )
        digitButtons.forEachIndexed { index, button ->
            button.setOnClickListener { onDigit(index.toString()) }
        }
        binding.keyDelete.setOnClickListener {
            if (enteredPin.isNotEmpty()) enteredPin.deleteCharAt(enteredPin.length - 1)
            updateDots()
        }
    }

    private fun onDigit(digit: String) {
        if (lockedOut) return
        if (enteredPin.length >= 4) return
        enteredPin.append(digit)
        updateDots()
        if (enteredPin.length == 4) checkPin()
    }

    private fun updateDots() {
        val dots = listOf(binding.dot1, binding.dot2, binding.dot3, binding.dot4)
        dots.forEachIndexed { i, dot -> dot.isSelected = i < enteredPin.length }
    }

    private fun checkPin() {
        val pin = enteredPin.toString()

        if (!crypto.isPinSet()) {
            // First run: this entry becomes the real vault PIN.
            crypto.setPinHash(pin)
            crypto.clearFailedAttempts()
            unlock(isDecoy = false)
            return
        }

        when {
            crypto.verifyPin(pin) -> {
                crypto.clearFailedAttempts()
                unlock(isDecoy = false)
            }
            crypto.verifyDecoyPin(pin) -> {
                // Opens a harmless, empty vault view — the real vault stays hidden.
                crypto.clearFailedAttempts()
                unlock(isDecoy = true)
            }
            else -> {
                val attempts = crypto.recordFailedAttempt()
                enteredPin.clear()
                updateDots()
                if (attempts >= 5) {
                    // Simple deterrent: brief cooldown after repeated wrong PINs,
                    // growing a little longer each time this keeps happening.
                    val cooldownMs = 3000L + (attempts - 5).coerceAtLeast(0) * 2000L
                    lockedOut = true
                    binding.lockoutMessage.text = "Too many attempts — wait ${cooldownMs / 1000}s"
                    binding.lockoutMessage.visibility = android.view.View.VISIBLE
                    binding.root.postDelayed({
                        lockedOut = false
                        binding.lockoutMessage.visibility = android.view.View.GONE
                    }, cooldownMs)
                } else {
                    binding.lockoutMessage.visibility = android.view.View.GONE
                }
            }
        }
    }

    private fun offerBiometricIfAvailable() {
        val manager = BiometricManager.from(this)
        val canUseBiometric = manager.canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_STRONG
        ) == BiometricManager.BIOMETRIC_SUCCESS

        binding.biometricButton.visibility =
            if (canUseBiometric && crypto.isPinSet()) android.view.View.VISIBLE else android.view.View.GONE

        binding.biometricButton.setOnClickListener {
            val executor = ContextCompat.getMainExecutor(this)
            val prompt = BiometricPrompt(this, executor, object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    unlock(isDecoy = false)
                }
            })
            val info = BiometricPrompt.PromptInfo.Builder()
                .setTitle("Unlock Vault")
                .setNegativeButtonText("Use PIN instead")
                .build()
            prompt.authenticate(info)
        }
    }

    private fun unlock(isDecoy: Boolean) {
        val intent = Intent(this, VaultGridActivity::class.java)
            .putExtra(VaultGridActivity.EXTRA_DECOY_MODE, isDecoy)
        startActivity(intent)
        finish()
    }
}
