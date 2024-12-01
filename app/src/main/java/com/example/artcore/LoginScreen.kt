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
fun LoginScreen(
    onLogin: (email: String, password: String) -> Unit,
    onSwitchToRegister: () -> Unit
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(text = "Вход", modifier = Modifier.padding(8.dp))
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
        Button(onClick = { onLogin(email, password) }, modifier = Modifier.padding(8.dp)) {
            Text(text = "Войти")
        }
        Button(onClick = onSwitchToRegister, modifier = Modifier.padding(8.dp)) {
            Text(text = "Нет аккаунта? Зарегистрироваться")
        }
    }
}
