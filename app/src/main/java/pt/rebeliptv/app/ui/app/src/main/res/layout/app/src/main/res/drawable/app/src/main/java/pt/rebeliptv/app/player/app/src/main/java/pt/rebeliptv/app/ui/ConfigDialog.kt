package pt.rebeliptv.app.ui

import android.app.AlertDialog
import android.content.Context
import android.text.InputType
import android.view.LayoutInflater
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import pt.rebeliptv.app.storage.SecureStorage

class ConfigDialog(
    private val context: Context,
    private val storage: SecureStorage,
    private val onSaved: () -> Unit
) {

    fun show() {
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 10, 40, 10)
        }

        val title = TextView(context).apply {
            text = "☠ Rebel IPTV"
            textSize = 20f
            setPadding(0, 0, 0, 20)
        }

        val hostInput = EditText(context).apply {
            hint = "Host (ex: http://servidor:8080)"
            inputType = InputType.TYPE_CLASS_TEXT or
                InputType.TYPE_TEXT_VARIATION_URI
            setText(storage.getHost().orEmpty())
        }

        val usernameInput = EditText(context).apply {
            hint = "Username"
            inputType = InputType.TYPE_CLASS_TEXT
            setText(storage.getUsername().orEmpty())
        }

        val passwordInput = EditText(context).apply {
            hint = "Password"
            inputType = InputType.TYPE_CLASS_TEXT or
                InputType.TYPE_TEXT_VARIATION_PASSWORD
            setText(storage.getPassword().orEmpty())
        }

        container.addView(title)
        container.addView(hostInput)
        container.addView(usernameInput)
        container.addView(passwordInput)

        AlertDialog.Builder(context)
            .setView(container)
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Guardar e ligar", null)
            .create()
            .also { dialog ->
                dialog.setOnShowListener {
                    dialog.getButton(
                        AlertDialog.BUTTON_POSITIVE
                    ).setOnClickListener {

                        val host = hostInput.text
                            .toString()
                            .trim()

                        val username = usernameInput.text
                            .toString()
                            .trim()

                        val password = passwordInput.text
                            .toString()

                        if (
                            host.isBlank() ||
                            username.isBlank() ||
                            password.isBlank()
                        ) {
                            Toast.makeText(
                                context,
                                "Preenche todos os campos.",
                                Toast.LENGTH_SHORT
                            ).show()
                            return@setOnClickListener
                        }

                        storage.saveConfiguration(
                            host = host,
                            username = username,
                            password = password
                        )

                        dialog.dismiss()
                        onSaved()
                    }
                }

                dialog.show()
            }
    }
}
