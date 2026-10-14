package ai.mwm.client.cloud

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

// Compatibility class for old setup entry points; Firebase is not probed for shared-data readiness.
data class FirebaseSetupState(val checking: Boolean=false,val message: String="Supabase · Sign in to connect")
class FirebaseSetupViewModel(app: Application): AndroidViewModel(app) {
    private val service=PipoFirebase.get(app)
    private val mutable=MutableStateFlow(FirebaseSetupState())
    val state: StateFlow<FirebaseSetupState> = mutable
    fun check() {
        if(mutable.value.checking)return
        mutable.value=FirebaseSetupState(true,"Checking Supabase session…")
        viewModelScope.launch {try {service.probe();mutable.value=FirebaseSetupState(message="Supabase session verified · Pairing can be requested")}
        catch(e: CancellationException){throw e} catch(e: Exception){mutable.value=FirebaseSetupState(message=connectionError(e))}}
    }
    fun resume() { /* Supabase session refresh is owned by PartnerSync. Push remains disabled. */ }
}
