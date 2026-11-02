package com.visualtrace.ai.features.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.visualtrace.ai.core.designsystem.LocalVtColors
import com.visualtrace.ai.core.ui.GradientButton
import com.visualtrace.ai.core.ui.VisualTraceLogo
import com.visualtrace.ai.features.R
import com.visualtrace.ai.features.common.toText
import kotlinx.coroutines.launch

@Composable
fun AuthScreen(viewModel: AuthViewModel, googleWebClientId: String) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showPassword by rememberSaveable { mutableStateOf(false) }
    val configured = viewModel.isConfigured

    Box(Modifier.fillMaxSize().background(LocalVtColors.current.heroGradient), contentAlignment = Alignment.Center) {
        Column(
            Modifier.widthIn(max = 480.dp).fillMaxWidth().verticalScroll(rememberScrollState()).imePadding().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            VisualTraceLogo(72.dp)
            Spacer(Modifier.height(16.dp))
            Text(stringResource(R.string.welcome), style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center,
                modifier = Modifier.semantics { heading() })
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.welcome_body), style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(24.dp))

            if (!configured) {
                Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.medium) {
                    Text(stringResource(R.string.auth_not_configured), Modifier.padding(16.dp), style = MaterialTheme.typography.bodyMedium)
                }
                Spacer(Modifier.height(16.dp))
            } else {
                OutlinedTextField(
                    value = state.email, onValueChange = viewModel::onEmail, label = { Text(stringResource(R.string.email)) },
                    singleLine = true, isError = state.emailError,
                    supportingText = if (state.emailError) {{ Text(stringResource(R.string.invalid_email)) }} else null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (state.mode == AuthMode.REGISTER) {
                    OutlinedTextField(state.name, viewModel::onName, label = { Text(stringResource(R.string.name_optional)) },
                        singleLine = true, modifier = Modifier.fillMaxWidth())
                }
                if (state.mode != AuthMode.FORGOT) {
                    OutlinedTextField(
                        value = state.password, onValueChange = viewModel::onPassword, label = { Text(stringResource(R.string.password)) },
                        singleLine = true, isError = state.passwordError,
                        supportingText = if (state.mode == AuthMode.REGISTER) {{ Text(stringResource(R.string.password_rules)) }} else null,
                        visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { showPassword = !showPassword }) {
                                Icon(if (showPassword) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility, stringResource(R.string.password))
                            }
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { viewModel.submit() }),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                state.error?.let {
                    val t = it.toText()
                    Text(t.message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                }
                state.info?.let {
                    Text(stringResource(if (it == AuthInfo.RESET_SENT) R.string.reset_sent else R.string.verification_sent),
                        color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                }
                Spacer(Modifier.height(16.dp))
                GradientButton(
                    text = stringResource(when (state.mode) {
                        AuthMode.SIGN_IN -> R.string.sign_in; AuthMode.REGISTER -> R.string.register; AuthMode.FORGOT -> R.string.reset_password }),
                    onClick = viewModel::submit, loading = state.busy, modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                when (state.mode) {
                    AuthMode.SIGN_IN -> {
                        TextButton(onClick = { viewModel.setMode(AuthMode.FORGOT) }) { Text(stringResource(R.string.forgot_password)) }
                        TextButton(onClick = { viewModel.setMode(AuthMode.REGISTER) }) { Text(stringResource(R.string.no_account)) }
                    }
                    AuthMode.REGISTER -> TextButton(onClick = { viewModel.setMode(AuthMode.SIGN_IN) }) { Text(stringResource(R.string.have_account)) }
                    AuthMode.FORGOT -> TextButton(onClick = { viewModel.setMode(AuthMode.SIGN_IN) }) { Text(stringResource(R.string.back_to_sign_in)) }
                }
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            when (val r = GoogleSignIn.request(context, googleWebClientId)) {
                                is GoogleSignIn.Result.Token -> viewModel.onGoogleIdToken(r.idToken)
                                is GoogleSignIn.Result.Error -> viewModel.onGoogleError(r.message)
                                GoogleSignIn.Result.Cancelled -> Unit
                            }
                        }
                    },
                    enabled = !state.busy,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                    shape = MaterialTheme.shapes.medium,
                ) {
                    Icon(Icons.Outlined.AccountCircle, null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.google_sign_in))
                }
            }
            Spacer(Modifier.height(12.dp))
            TextButton(onClick = { viewModel.continueAsGuest() }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.continue_guest), style = MaterialTheme.typography.titleSmall)
            }
        }
    }
}
