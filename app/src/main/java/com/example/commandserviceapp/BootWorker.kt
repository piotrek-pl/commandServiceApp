import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters

class BootWorker(context: Context, workerParams: WorkerParameters) : Worker(context, workerParams) {
    override fun doWork(): Result {
        // Tutaj wykonaj zadanie w tle
        return Result.success()
    }
}