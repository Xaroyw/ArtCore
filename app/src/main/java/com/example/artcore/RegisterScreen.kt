package com.example.artcore

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

@Composable
fun RegisterScreen(
    onRegister: (email: String, password: String, nickname: String) -> Unit,
    onSwitchToLogin: () -> Unit
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var nickname by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(text = "Регистрация", modifier = Modifier.padding(8.dp))

        // Поле для ввода никнейма
        BasicTextField(
            value = nickname,
            onValueChange = { nickname = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            decorationBox = { innerTextField ->
                Text(text = if (nickname.isEmpty()) "Введите Никнейм" else nickname)
                innerTextField()
            }
        )

        // Поле для ввода email
        BasicTextField(
            value = email,
            onValueChange = { email = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            decorationBox = { innerTextField ->
                Text(text = if (email.isEmpty()) "Введите Email" else email)
                innerTextField()
            }
        )

        // Поле для ввода пароля
        BasicTextField(
            value = password,
            onValueChange = { password = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            visualTransformation = PasswordVisualTransformation(),
            decorationBox = { innerTextField ->
                Text(text = if (password.isEmpty()) "Введите Пароль" else "")
                innerTextField()
            }
        )

        Button(onClick = { onRegister(email, password, nickname) }, modifier = Modifier.padding(8.dp)) {
            Text(text = "Зарегистрироваться")
        }

        Button(onClick = onSwitchToLogin, modifier = Modifier.padding(8.dp)) {
            Text(text = "Уже есть аккаунт? Войти")
        }
    }
}

