package ai.mwm.client.ui.screens

import ai.mwm.client.cloud.PartnerConnection
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

@Composable fun PairingSignInDialog(state: PartnerConnection, onDismiss: () -> Unit, onSubmit: (String,String,Boolean) -> Unit) {
    var email by remember {mutableStateOf("")}
    // Deliberately not rememberSaveable: credentials never enter saved instance state or disk.
    var password by remember {mutableStateOf("")}
    var signup by remember {mutableStateOf(false)}
    AlertDialog(onDismissRequest={if(!state.busy)onDismiss()},title={PText(if(signup) "Create your account" else "Log in")},text={
        Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            PText("Each partner uses a separate account. Account backup and partner sharing have separate controls. If you started creating or joining a space, that action continues after login.",style=MaterialTheme.typography.bodySmall)
            OutlinedTextField(email,{email=it},modifier=Modifier.fillMaxWidth(),label={Text("Email")},singleLine=true,enabled=!state.busy,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Email))
            OutlinedTextField(password,{password=it},modifier=Modifier.fillMaxWidth(),label={Text("Password")},singleLine=true,enabled=!state.busy,visualTransformation=PasswordVisualTransformation(),keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Password),supportingText=if(signup)({PText("At least 8 characters",style=MaterialTheme.typography.bodySmall)}) else null)
            if(state.pairingMessage.isNotBlank()) PText(state.pairingMessage,style=MaterialTheme.typography.bodySmall)
            TextButton({signup=!signup},enabled=!state.busy) {Text(if(signup && state.pairingMessage.startsWith("Check your email to confirm")) "Email confirmed? Log in" else if(signup) "Already have an account? Log in" else "Create an account")}
        }
    },confirmButton={Button({onSubmit(email,password,signup)},enabled=!state.busy && email.contains("@") && password.isNotBlank() && (!signup || password.length>=8)) {Text(if(state.busy) "Please wait…" else if(signup) "Create account" else "Log in")}},dismissButton={TextButton(onDismiss,enabled=!state.busy){Text("Cancel")}})
}
