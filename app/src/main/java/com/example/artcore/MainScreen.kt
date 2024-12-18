package com.example.artcore

import android.net.Uri
import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.rememberImagePainter
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.storage.FirebaseStorage
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.platform.LocalDensity
import coil.compose.rememberImagePainter
import coil.compose.AsyncImagePainter
import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.IntSize
import com.google.firebase.database.DatabaseReference
import com.google.accompanist.swiperefresh.SwipeRefresh
import com.google.accompanist.swiperefresh.rememberSwipeRefreshState
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.Color


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    onNavigateToProfile: () -> Unit
) {
    val auth: FirebaseAuth = FirebaseAuth.getInstance()
    val database = FirebaseDatabase.getInstance().reference
    val storage: FirebaseStorage = FirebaseStorage.getInstance()

    var imageUri by remember { mutableStateOf<Uri?>(null) }
    var nickname by remember { mutableStateOf("Загрузка...") }
    var images by remember { mutableStateOf(listOf<Pair<String, String>>()) } // (Image URL, Uploader Nickname)
    var isRefreshing by remember { mutableStateOf(false) }
    var isImageOpen by remember { mutableStateOf(false) }
    var isStatsDialogOpen by remember { mutableStateOf(false) }
    var userCount by remember { mutableStateOf(0) }
    var imageCount by remember { mutableStateOf(0) }

    // Создаем корутинный scope
    val coroutineScope = rememberCoroutineScope()

    // Функция загрузки изображений
    suspend fun loadImages() {
        val userId = auth.currentUser?.uid
        if (userId != null) {
            val userProfileRef = database.child("users").child(userId)

            userProfileRef.get().addOnSuccessListener { snapshot ->
                nickname = snapshot.child("nickname").getValue(String::class.java) ?: "Никнейм не найден"
                val updatedImageUrl = snapshot.child("profileImageUrl").getValue(String::class.java)
                imageUri = updatedImageUrl?.let { Uri.parse(it) }
                Log.d("MainScreen", "User profile loaded: $nickname, imageUri: $imageUri")
            }.addOnFailureListener {
                Log.e("MainScreen", "Failed to load user profile", it)
            }
        }

        // Загрузка изображений ленты
        val imagesRef = database.child("allImages") // Предполагается, что изображения находятся в "allImages"
        imagesRef.get().addOnSuccessListener { snapshot ->
            val fetchedImages = snapshot.children.mapNotNull { imageSnapshot ->
                val imageUrl = imageSnapshot.child("imageUrl").getValue(String::class.java)
                val uploaderNickname = imageSnapshot.child("nickname").getValue(String::class.java)
                if (imageUrl != null && uploaderNickname != null) imageUrl to uploaderNickname else null
            }

            // Фильтрация изображений, исключаем те, которые принадлежат текущему пользователю
            val filteredImages = fetchedImages.filter { it.second != nickname }

            // Логирование количества полученных изображений
            Log.d("MainScreen", "Fetched ${filteredImages.size} images from database, excluding current user's images")

            // Случайная сортировка изображений
            images = filteredImages.shuffled() // Перемешиваем изображения
        }.addOnFailureListener {
            Log.e("MainScreen", "Failed to load images", it)
        }
    }

    LaunchedEffect(Unit) {
        loadUserCount(database) { count ->
            userCount = count
            Log.d("MainScreen", "User count: $count")
        }
        loadImageCount(database) { count ->
            imageCount = count
            Log.d("MainScreen", "Image count: $count")
        }
        loadImages()
    }

    Scaffold(
        topBar = {
            if (!isImageOpen) {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Image(
                                painter = rememberImagePainter(
                                    data = imageUri ?: R.drawable.ic_default_avatar
                                ),
                                contentDescription = "User Avatar",
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .clickable { onNavigateToProfile() },
                                contentScale = ContentScale.Crop
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("ArtCore")
                        }
                    },
                    actions = {
                        Button(onClick = { isStatsDialogOpen = true }) {
                            Text("Статистика")
                        }
                    }
                )
            }
        }
    ) { paddingValues ->
        var selectedImageUrl by remember { mutableStateOf("") }

        SwipeRefresh(
            state = rememberSwipeRefreshState(isRefreshing),
            onRefresh = {
                // Запускаем корутину в scope
                coroutineScope.launch {
                    isRefreshing = true
                    loadImages()
                    isRefreshing = false
                }
            }
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                items(images) { (imageUrl, uploaderNickname) ->
                    ImageCard(
                        imageUrl = imageUrl,
                        uploaderNickname = uploaderNickname,
                        onImageClick = {
                            selectedImageUrl = imageUrl
                            isImageOpen = true
                        }
                    )
                }
            }
        }

        if (isStatsDialogOpen) {
            StatsDialog(
                userCount = userCount,
                imageCount = imageCount,
                onDismiss = { isStatsDialogOpen = false }
            )
        }

        if (isImageOpen) {
            FullScreenImage(
                imageUrl = selectedImageUrl,
                auth = auth,
                database = database,
                onClose = { isImageOpen = false }
            )
        }
    }
}

@Composable
fun StatsDialog(userCount: Int, imageCount: Int, onDismiss: () -> Unit) {
    androidx.compose.material.AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = "Статистика")
        },
        text = {
            Column {
                Text(text = "Пользователей: $userCount")
                Text(text = "Изображений: $imageCount")
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("Закрыть")
            }
        }
    )
}

@Composable
fun ImageCard(
    imageUrl: String,
    uploaderNickname: String,
    onImageClick: () -> Unit
) {
    val painter = rememberImagePainter(imageUrl)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp)
            .clickable { onImageClick() }
    ) {
        Image(
            painter = painter,
            contentDescription = "Uploaded Image",
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 300.dp), // Ограничиваем максимальную высоту
            contentScale = ContentScale.Fit // Изображение будет полностью помещаться в контейнер
        )

        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Опубликовал: $uploaderNickname",
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
fun FullScreenImage(
    imageUrl: String,
    auth: FirebaseAuth, // Передаем объект FirebaseAuth
    database: DatabaseReference, // Передаем объект DatabaseReference
    onClose: () -> Unit
) {
    val painter = rememberImagePainter(imageUrl)

    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset(0f, 0f)) }
    var isLiked by remember { mutableStateOf(false) } // Состояние для лайка

    val maxScale = 3f
    val minScale = 1f

    // Проверка, лайкнуто ли изображение
    val userId = auth.currentUser?.uid
    userId?.let {
        val likesRef = database.child("users").child(userId).child("likes")
        likesRef.get().addOnSuccessListener { snapshot ->
            val existingLikes = snapshot.children.mapNotNull { it.getValue(String::class.java) }
            isLiked = existingLikes.contains(imageUrl)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .clickable(
                onClick = { /* Пустое действие для блокировки кликов под слоем */ },
                indication = null, // Убираем эффект нажатия
                interactionSource = remember { MutableInteractionSource() } // Не сохраняем состояние
            )
    ) {
        Image(
            painter = painter,
            contentDescription = "Full screen image",
            modifier = Modifier
                .fillMaxSize()
                .align(Alignment.Center)
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        // Расчет нового масштаба
                        scale = (scale * zoom).coerceIn(minScale, maxScale)

                        // Обновление смещения с учетом панорамирования, удерживая изображение в пределах экрана
                        if (scale == minScale) {
                            offset = Offset(0f, 0f)
                        } else {
                            val newOffsetX = offset.x + pan.x
                            val newOffsetY = offset.y + pan.y

                            // Ограничиваем смещение, чтобы изображение не выходило за пределы экрана
                            val maxOffsetX = (size.width * scale - size.width) / 2
                            val maxOffsetY = (size.height * scale - size.height) / 2

                            offset = Offset(
                                x = newOffsetX.coerceIn(-maxOffsetX, maxOffsetX),
                                y = newOffsetY.coerceIn(-maxOffsetY, maxOffsetY)
                            )
                        }
                    }
                }
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    translationX = offset.x,
                    translationY = offset.y
                ),
            contentScale = ContentScale.Fit
        )

        // Кнопка "Лайк"
        Button(
            onClick = {
                userId?.let { uid ->
                    val likesRef = database.child("users").child(uid).child("likes")
                    if (isLiked) {
                        // Если лайк уже есть, удаляем его
                        likesRef.get().addOnSuccessListener { snapshot ->
                            // Находим элемент с изображением и удаляем его
                            val likeToRemove = snapshot.children.find { it.getValue(String::class.java) == imageUrl }
                            likeToRemove?.ref?.removeValue()
                                ?.addOnSuccessListener {
                                    Log.d("FullScreenImage", "Image unliked successfully")
                                    // Обновляем состояние лайка после успешного удаления
                                    isLiked = false
                                }
                                ?.addOnFailureListener {
                                    Log.e("FullScreenImage", "Failed to unlike image", it)
                                }
                        }
                    } else {
                        // Если лайка нет, добавляем его
                        likesRef.push().setValue(imageUrl)
                            .addOnSuccessListener {
                                Log.d("FullScreenImage", "Image liked successfully")
                                // Обновляем состояние лайка после успешного добавления
                                isLiked = true
                            }
                            .addOnFailureListener {
                                Log.e("FullScreenImage", "Failed to like image", it)
                            }
                    }
                }
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
                .background(Color.Transparent) // Убираем фон кнопки
                .then(Modifier.border(0.dp, Color.Transparent)) // Убираем бордер кнопки
        ) {
            // Используем Icon для отображения сердца
            Icon(
                imageVector = if (isLiked) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                contentDescription = if (isLiked) "Liked" else "Not Liked",
                tint = Color.Red, // Красный цвет для иконки
                modifier = Modifier.size(40.dp) // Размер иконки
            )
        }

        // Кнопка "Закрыть" в правом нижнем углу с овальной формой
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(16.dp)
        ) {
            Button(
                onClick = onClose,
                modifier = Modifier
                    .wrapContentWidth() // Размер кнопки под текст
                    .clip(RoundedCornerShape(50)) // Овальная форма
                    .background(MaterialTheme.colorScheme.primary)
            ) {
                Text("Закрыть")
            }
        }
    }
}

// Функция для подсчёта пользователей
fun loadUserCount(database: DatabaseReference, onResult: (Int) -> Unit) {
    database.child("users").get()
        .addOnSuccessListener { snapshot ->
            val count = snapshot.childrenCount.toInt()
            onResult(count)
        }
        .addOnFailureListener { exception ->
            Log.e("MainScreen", "Failed to load user count", exception)
            onResult(0)
        }
}

// Функция для подсчёта изображений
fun loadImageCount(database: DatabaseReference, onResult: (Int) -> Unit) {
    database.child("allImages").get()
        .addOnSuccessListener { snapshot ->
            val count = snapshot.childrenCount.toInt()
            onResult(count)
        }
        .addOnFailureListener { exception ->
            Log.e("MainScreen", "Failed to load image count", exception)
            onResult(0)
        }
}