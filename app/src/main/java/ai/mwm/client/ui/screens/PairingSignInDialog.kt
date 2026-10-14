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
    AlertDialog(onDismissRequest={if(!state.busy)onDismiss()},title={PText(if(signup) "Create your Supabase account" else "Sign in to pair")},text={
        Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            PText("Each partner needs a separate confirmed email account. After sign-in, your selected pairing action continues. Your local tasks and private health vault are not uploaded.",style=MaterialTheme.typography.bodySmall)
            OutlinedTextField(email,{email=it},label={Text("Email")},singleLine=true,enabled=!state.busy,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Email))
            OutlinedTextField(password,{password=it},label={Text("Password")},singleLine=true,enabled=!state.busy,visualTransformation=PasswordVisualTransformation(),keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Password))
            if(state.pairingMessage.isNotBlank()) PText(state.pairingMessage,style=MaterialTheme.typography.bodySmall)
            TextButton({signup=!signup},enabled=!state.busy) {Text(if(signup) "Already have an account? Sign in" else "Need an account? Sign up")}
        }
    },confirmButton={Button({onSubmit(email,password,signup)},enabled=!state.busy && email.contains("@") && password.isNotBlank() && (!signup || password.length>=8)) {Text(if(state.busy) "Please wait…" else if(signup) "Sign up" else "Sign in")}},dismissButton={TextButton(onDismiss,enabled=!state.busy){Text("Cancel")}})
}
