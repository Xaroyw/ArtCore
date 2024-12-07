package com.example.artcore

import android.net.Uri
import android.util.Log
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.dp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.storage.FirebaseStorage
import coil.compose.rememberImagePainter
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.PasswordVisualTransformation

@Composable
fun EditProfile(
    email: String,
    nickname: String,
    onLogout: () -> Unit,
    onBack: () -> Unit
) {
    var imageUri by remember { mutableStateOf<Uri?>(null) }
    var newNickname by remember { mutableStateOf(nickname) }
    var currentNickname by remember { mutableStateOf(nickname) }  // Новый state для текущего никнейма
    var isEditingNickname by remember { mutableStateOf(false) }
    var isChangesPending by remember { mutableStateOf(false) }
    var showFullAvatar by remember { mutableStateOf(false) }
    var currentPassword by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var isPasswordValid by remember { mutableStateOf(false) }
    var isChangingPassword by remember { mutableStateOf(false) }
    var isNicknameUnique by remember { mutableStateOf(true) }
    var nicknameErrorMessage by remember { mutableStateOf("") }
    var passwordErrorMessage by remember { mutableStateOf("") }

    val auth: FirebaseAuth = FirebaseAuth.getInstance()
    val storage: FirebaseStorage = FirebaseStorage.getInstance()
    val database = FirebaseDatabase.getInstance().reference.child("users")

    // Загрузка изображения профиля
    val uploadImage = { uri: Uri ->
        val userId = auth.currentUser?.uid
        if (userId != null) {
            val storageRef = storage.reference.child("profile_images").child(userId)
            storageRef.putFile(uri).addOnSuccessListener {
                storageRef.downloadUrl.addOnSuccessListener { downloadUri ->
                    database.child(userId).child("profileImageUrl").setValue(downloadUri.toString())
                        .addOnSuccessListener {
                            imageUri = downloadUri
                            isChangesPending = true
                        }
                }.addOnFailureListener {
                    Log.e("EditProfile", "Error getting download URL: ${it.message}")
                }
            }.addOnFailureListener {
                Log.e("EditProfile", "Error uploading profile image: ${it.message}")
            }
        }
    }

    // Получение URL изображения из базы данных
    LaunchedEffect(auth.currentUser?.uid) {
        auth.currentUser?.uid?.let { userId ->
            database.child(userId).child("profileImageUrl").get().addOnSuccessListener { snapshot ->
                val imageUrl = snapshot.value as? String
                imageUri = imageUrl?.let { Uri.parse(it) }
            }.addOnFailureListener {
                Log.e("EditProfile", "Error fetching profile image URL: ${it.message}")
            }

            // Получаем актуальный никнейм
            database.child(userId).child("nickname").get().addOnSuccessListener { snapshot ->
                currentNickname = snapshot.value as? String ?: ""
                newNickname = currentNickname  // Обновляем newNickname, чтобы оно отображалось
            }.addOnFailureListener {
                Log.e("EditProfile", "Error fetching nickname: ${it.message}")
            }
        }
    }

    // Запуск выбора изображения
    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            imageUri = uri
            uploadImage(uri)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Отображение аватара
        val painter: Painter = rememberImagePainter(
            data = imageUri ?: R.drawable.ic_default_avatar,
            builder = {
                crossfade(true)
                memoryCacheKey(imageUri.toString())
            }
        )
        if (showFullAvatar) {
            // Показываем аватар в полном размере
            Box(modifier = Modifier.fillMaxSize()) {
                Image(
                    painter = painter,
                    contentDescription = "Profile Image",
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentScale = ContentScale.Fit
                )
                // Кнопки внутри Box, чтобы они отображались поверх изображения
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(32.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        // Кнопка для изменения аватарки
                        Button(onClick = { pickImage.launch("image/*") }) {
                            Text("Изменить аватар")
                        }
                        // Кнопка для закрытия полноэкранного режима
                        Button(onClick = { showFullAvatar = false }) {
                            Text("Закрыть")
                        }
                    }
                }
            }
        } else {
            // Отображаем аватар в обычном размере
            Image(
                painter = painter,
                contentDescription = "Profile Image",
                modifier = Modifier
                    .size(120.dp)
                    .clip(CircleShape)
                    .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
                    .clickable { showFullAvatar = true },
                contentScale = ContentScale.Crop
            )
        }


        Spacer(modifier = Modifier.height(16.dp))

        // Почта
        Text(text = "Почта: $email", style = MaterialTheme.typography.bodyMedium)

        Spacer(modifier = Modifier.height(16.dp))

        // Отображение текущего никнейма
        if (isEditingNickname) {
            OutlinedTextField(
                value = newNickname,
                onValueChange = {
                    newNickname = it
                    isChangesPending = true
                },
                label = { Text("Никнейм") },
                isError = newNickname.isEmpty(),  // Проверка на пустое поле
                modifier = Modifier.fillMaxWidth().padding(8.dp),
            )

            if (newNickname.isEmpty()) {
                Text(text = "Никнейм не может быть пустым!", color = MaterialTheme.colorScheme.error)
            }

            // Отображение ошибки для занятого никнейма
            if (!isNicknameUnique) {
                Text(text = nicknameErrorMessage, color = MaterialTheme.colorScheme.error)
            }

            Row {
                Button(onClick = {
                    isEditingNickname = false
                    newNickname = currentNickname // Возвращаем старое значение, если отменить
                }) {
                    Text("Отмена")
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(onClick = {
                    if (newNickname.isNotEmpty()) {
                        val userId = auth.currentUser?.uid
                        if (userId != null) {
                            // Проверяем, существует ли уже никнейм в базе данных
                            database.orderByChild("nickname").equalTo(newNickname).get().addOnSuccessListener { snapshot ->
                                if (snapshot.exists()) {
                                    // Если никнейм уже существует, выводим ошибку
                                    isNicknameUnique = false
                                    nicknameErrorMessage = "Этот никнейм уже занят."
                                } else {
                                    // Если никнейм уникален, обновляем его
                                    database.child(userId).child("nickname").setValue(newNickname).addOnSuccessListener {
                                        currentNickname = newNickname // Обновляем текущий никнейм
                                        isChangesPending = false
                                        isEditingNickname = false
                                        Log.d("EditProfile", "Nickname updated successfully")
                                    }.addOnFailureListener {
                                        Log.e("EditProfile", "Error updating nickname: ${it.message}")
                                    }
                                }
                            }.addOnFailureListener {
                                Log.e("EditProfile", "Error checking nickname availability: ${it.message}")
                            }
                        }
                    }
                }) {
                    Text("Сохранить изменения")
                }
            }
        } else {
            Text("Никнейм: $currentNickname")  // Отображаем актуальный никнейм
            Button(onClick = { isEditingNickname = true }) {
                Text("Редактировать никнейм")
            }
        }


        Spacer(modifier = Modifier.height(24.dp))

        // Кнопка для изменения пароля
        Button(onClick = {
            isChangingPassword = true
            currentPassword = "" // Сбрасываем поле для текущего пароля при нажатии на "Изменить пароль"
        }) {
            Text("Изменить пароль")
        }

        // Функция для проверки пароля
        val verifyPassword = { password: String ->
            if (password.isEmpty()) {
                passwordErrorMessage = "Поле для пароля не может быть пустым!"
                isPasswordValid = false
            } else {
                val user = auth.currentUser
                if (user != null) {
                    auth.signInWithEmailAndPassword(user.email!!, password).addOnCompleteListener { task ->
                        if (task.isSuccessful) {
                            isPasswordValid = true
                            isChangingPassword = true
                            passwordErrorMessage = ""
                        } else {
                            isPasswordValid = false
                            passwordErrorMessage = "Неверный пароль!"
                        }
                    }
                }
            }
        }

        // Функция для проверки совпадения паролей
        val checkPasswordsMatch = { newPassword: String, currentPassword: String ->
            if (newPassword.isEmpty() || currentPassword.isEmpty()) {
                passwordErrorMessage = "Пароли не могут быть пустыми."
                false
            } else if (newPassword == currentPassword) {
                passwordErrorMessage = "Новый пароль не должен совпадать с текущим."
                false
            } else {
                passwordErrorMessage = "" // Очистка ошибки, если пароли не совпадают
                true
            }
        }

        // Обновленный блок кода, где происходит изменение пароля
        if (isChangingPassword) {
            if (isPasswordValid) {
                // Поле для нового пароля
                OutlinedTextField(
                    value = newPassword,
                    onValueChange = {
                        newPassword = it
                        // Проверка на совпадение паролей при изменении значения нового пароля
                        if (!checkPasswordsMatch(newPassword, currentPassword)) {
                            // Если пароли совпадают, ошибка уже будет установлена в passwordErrorMessage
                        }
                    },
                    label = { Text("Новый пароль") },
                    modifier = Modifier.fillMaxWidth().padding(8.dp),
                    visualTransformation = PasswordVisualTransformation()
                )

                // Отображение ошибки, если она есть
                if (passwordErrorMessage.isNotEmpty()) {
                    Text(text = passwordErrorMessage, color = MaterialTheme.colorScheme.error)
                }

                Row {
                    Button(onClick = {
                        if (checkPasswordsMatch(newPassword, currentPassword)) {
                            if (newPassword.isNotEmpty()) {
                                auth.currentUser?.updatePassword(newPassword)?.addOnSuccessListener {
                                    Log.d("EditProfile", "Password updated successfully")
                                    isChangingPassword = false
                                }?.addOnFailureListener {
                                    Log.e("EditProfile", "Error updating password: ${it.message}")
                                }
                            } else {
                                passwordErrorMessage = "Новый пароль не может быть пустым."
                            }
                        }
                    }) {
                        Text("Изменить пароль")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(onClick = {
                        // При отмене сбрасываем состояние
                        isChangingPassword = false
                        isPasswordValid = false
                        currentPassword = "" // Очищаем поле текущего пароля
                        passwordErrorMessage = "" // Очищаем ошибку
                    }) {
                        Text("Отмена")
                    }
                }
            } else {
                // Проверка текущего пароля
                OutlinedTextField(
                    value = currentPassword,
                    onValueChange = { currentPassword = it },
                    label = { Text("Текущий пароль") },
                    modifier = Modifier.fillMaxWidth().padding(8.dp),
                    visualTransformation = PasswordVisualTransformation()
                )
                if (passwordErrorMessage.isNotEmpty()) {
                    Text(text = passwordErrorMessage, color = MaterialTheme.colorScheme.error)
                }
                Row {
                    Button(onClick = { verifyPassword(currentPassword) }) {
                        Text("Ввод")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(onClick = {
                        // При отмене сбрасываем состояние
                        isChangingPassword = false
                        isPasswordValid = false // Сбрасываем состояние проверки пароля
                        currentPassword = "" // Очищаем поле для пароля
                        passwordErrorMessage = "" // Очищаем ошибку
                    }) {
                        Text("Отмена")
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(onClick = onBack) {
            Text("Назад")
        }

        // Кнопка выхода
        Button(onClick = onLogout) {
            Text("Выйти")
        }
    }
}

