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
import androidx.compose.ui.unit.sp

@Composable
fun LoginScreen(
    onLogin: (identifier: String, password: String) -> Unit,
    onSwitchToRegister: () -> Unit
) {
    var identifier by remember { mutableStateOf("") }  // Здесь будет как email, так и никнейм
    var password by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Большая надпись "ArtCore"
        Text(
            text = "ArtCore",
            style = androidx.compose.ui.text.TextStyle(fontSize = 32.sp),
            modifier = Modifier.padding(bottom = 32.dp)
        )

        Text(text = "Вход", modifier = Modifier.padding(8.dp))

        // Поле для ввода email или никнейма
        BasicTextField(
            value = identifier,
            onValueChange = { identifier = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            decorationBox = { innerTextField ->
                Text(text = if (identifier.isEmpty()) "Введите Email или Никнейм" else identifier)
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

        Button(onClick = { onLogin(identifier, password) }, modifier = Modifier.padding(8.dp)) {
            Text(text = "Войти")
        }

        Button(onClick = onSwitchToRegister, modifier = Modifier.padding(8.dp)) {
            Text(text = "Нет аккаунта? Зарегистрироваться")
        }
    }
}

