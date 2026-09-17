package com.cardhunt.app.ui.auth

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.navigation.compose.hiltViewModel
import com.cardhunt.app.R

@Composable
fun LoginScreen(vm: AuthViewModel = hiltViewModel()) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    var username by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }

    Surface(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Spacer(Modifier.height(48.dp))
            Text(text = stringResource(R.string.app_name), style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Black)
            Text("Find cards in the real world. Shoot them. Collect them.",
                style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(24.dp))

            OutlinedTextField(username, { username = it }, Modifier.fillMaxWidth(),
                label = { Text("Username or email") }, singleLine = true)

            if (ui.isRegisterMode) {
                OutlinedTextField(email, { email = it }, Modifier.fillMaxWidth(),
                    label = { Text("Email") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email))
            }

            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Password") },
                singleLine = true,
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                trailingIcon = {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(
                            imageVector = if (passwordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                            contentDescription = if (passwordVisible) "Hide password" else "Show password"
                        )
                    }
                }
            )

            ui.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall)
            }

            Button(
                onClick = { vm.submit(username, email, password) },
                enabled = !ui.loading,
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                if (ui.loading) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                else Text(if (ui.isRegisterMode) "Create account" else "Log in")
            }

            TextButton(onClick = { vm.toggleMode() }, Modifier.align(Alignment.CenterHorizontally)) {
                Text(if (ui.isRegisterMode)
                    "Already have an account? Log in"
                else "New hunter? Create an account")
            }
        }
    }
}