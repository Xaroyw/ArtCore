package com.example.artcore

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase

class MainActivity : ComponentActivity() {
    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    private val database by lazy { FirebaseDatabase.getInstance().reference.child("users") }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            // Состояние для отслеживания текущего экрана
            var currentScreen by remember { mutableStateOf<Screen>(Screen.Login) }

            // Компоненты в зависимости от текущего экрана
            when (val screen = currentScreen) {
                is Screen.Login -> LoginScreen(
                    onLogin = { identifier, password -> loginUser(identifier, password, { newScreen -> currentScreen = newScreen }) },
                    onSwitchToRegister = { currentScreen = Screen.Register }
                )
                is Screen.Register -> RegisterScreen(
                    onRegister = { email, password, nickname -> registerUser(email, password, nickname, { newScreen -> currentScreen = newScreen }) },
                    onSwitchToLogin = { currentScreen = Screen.Login }
                )
                is Screen.Profile -> ProfileScreen(
                    email = screen.email,
                    nickname = screen.nickname,
                    onEditProfile = {
                        currentScreen = Screen.EditProfile(screen.email, screen.nickname)
                    }
                )
                is Screen.EditProfile -> EditProfile(
                    email = screen.email,
                    nickname = screen.nickname,
                    onLogout = {
                        auth.signOut()
                        currentScreen = Screen.Login
                    },
                    onBack = {
                        currentScreen = Screen.Profile(screen.email, screen.nickname) // Возврат к профилю
                    }
                )
            }
        }
    }

    sealed class Screen {
        object Login : Screen()
        object Register : Screen()
        data class Profile(val email: String, val nickname: String) : Screen()
        data class EditProfile(val email: String, val nickname: String) : Screen()
    }

    // Вход по email или никнейму
    private fun loginUser(identifier: String, password: String, updateScreen: (Screen) -> Unit) {
        if (identifier.contains("@")) {
            // Вход через email
            auth.signInWithEmailAndPassword(identifier, password)
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        val userId = auth.currentUser?.uid ?: return@addOnCompleteListener
                        database.child(userId).get()
                            .addOnSuccessListener { snapshot ->
                                val email = snapshot.child("email").value as? String ?: ""
                                val nickname = snapshot.child("nickname").value as? String ?: ""
                                updateScreen(Screen.Profile(email, nickname))  // Обновление экрана на профиль
                            }
                            .addOnFailureListener {
                                Toast.makeText(this, "Ошибка загрузки профиля", Toast.LENGTH_SHORT).show()
                            }
                    } else {
                        // Обработка ошибки при входе
                        val errorMessage = task.exception?.message ?: "Неизвестная ошибка"
                        if (errorMessage.contains("There is no user record")) {
                            Toast.makeText(this, "Аккаунт не найден", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(this, "Ошибка: $errorMessage", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
        } else {
            // Вход через никнейм
            database.orderByChild("nickname").equalTo(identifier)
                .get()
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        val result = task.result
                        if (result.childrenCount > 0) {
                            val user = result.children.firstOrNull()
                            val email = user?.child("email")?.value as? String
                            val userId = user?.key
                            if (email != null && userId != null) {
                                auth.signInWithEmailAndPassword(email, password)
                                    .addOnCompleteListener { loginTask ->
                                        if (loginTask.isSuccessful) {
                                            database.child(userId).get()
                                                .addOnSuccessListener { snapshot ->
                                                    val nickname = snapshot.child("nickname").value as? String ?: ""
                                                    updateScreen(Screen.Profile(email, nickname))  // Обновление экрана на профиль
                                                }
                                                .addOnFailureListener {
                                                    Toast.makeText(this, "Ошибка загрузки профиля", Toast.LENGTH_SHORT).show()
                                                }
                                        } else {
                                            Toast.makeText(this, "Ошибка: ${loginTask.exception?.message}", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                            } else {
                                Toast.makeText(this, "Пользователь не найден", Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            Toast.makeText(this, "Никнейм не найден", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        Toast.makeText(this, "Ошибка поиска: ${task.exception?.message}", Toast.LENGTH_SHORT).show()
                    }
                }
        }
    }


    // Регистрация пользователя
    private fun registerUser(email: String, password: String, nickname: String, updateScreen: (Screen) -> Unit) {
        // Проверка уникальности email
        database.orderByChild("email").equalTo(email).get()
            .addOnCompleteListener { emailTask ->
                if (emailTask.isSuccessful) {
                    val emailExists = emailTask.result.childrenCount > 0
                    if (emailExists) {
                        // Email уже зарегистрирован
                        Toast.makeText(
                            this,
                            "Пользователь с таким email уже существует",
                            Toast.LENGTH_SHORT
                        ).show()
                    } else {
                        // Проверка уникальности nickname
                        database.orderByChild("nickname").equalTo(nickname).get()
                            .addOnCompleteListener { nicknameTask ->
                                if (nicknameTask.isSuccessful) {
                                    val nicknameExists = nicknameTask.result.childrenCount > 0
                                    if (nicknameExists) {
                                        // Nickname уже зарегистрирован
                                        Toast.makeText(
                                            this,
                                            "Пользователь с таким никнеймом уже существует",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    } else {
                                        // Регистрация нового пользователя
                                        auth.createUserWithEmailAndPassword(email, password)
                                            .addOnCompleteListener { registerTask ->
                                                if (registerTask.isSuccessful) {
                                                    val userId = auth.currentUser?.uid
                                                        ?: return@addOnCompleteListener
                                                    database.child(userId).setValue(
                                                        mapOf(
                                                            "email" to email,
                                                            "nickname" to nickname
                                                        )
                                                    )
                                                        .addOnSuccessListener {
                                                            Toast.makeText(
                                                                this,
                                                                "Регистрация успешна! Пожалуйста, войдите.",
                                                                Toast.LENGTH_SHORT
                                                            ).show()
                                                            updateScreen(Screen.Login)
                                                        }
                                                        .addOnFailureListener { dbError ->
                                                            Toast.makeText(
                                                                this,
                                                                "Ошибка сохранения профиля: ${dbError.message}",
                                                                Toast.LENGTH_SHORT
                                                            ).show()
                                                        }
                                                } else {
                                                    // Ошибка при регистрации в Firebase Authentication
                                                    Toast.makeText(
                                                        this,
                                                        "Ошибка регистрации: ${registerTask.exception?.message}",
                                                        Toast.LENGTH_SHORT
                                                    ).show()
                                                }
                                            }
                                    }
                                } else {
                                    Toast.makeText(
                                        this,
                                        "Ошибка проверки никнейма: ${nicknameTask.exception?.message}",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                    }
                } else {
                    Toast.makeText(
                        this,
                        "Ошибка проверки email: ${emailTask.exception?.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
    }

}




