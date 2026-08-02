import rikka.shizuku.Shizuku
fun main() {
    Shizuku.newProcess(arrayOf("sh", "-c", "echo hello"), null, null)
}
