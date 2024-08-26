import android.content.Context
import android.content.Intent
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.example.commandserviceapp.CommandService

class BootWorker(context: Context, workerParams: WorkerParameters) : Worker(context, workerParams) {

    override fun doWork(): Result {
        // Uruchomienie usługi w trybie pierwszoplanowym
        val intent = Intent(applicationContext, CommandService::class.java)
        applicationContext.startForegroundService(intent)

        return Result.success()
    }
}