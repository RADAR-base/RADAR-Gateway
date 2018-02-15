import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import org.radarcns.auth.config.YamlServerConfig
import org.radarcns.gateway.Config
import org.radarcns.gateway.GrizzlyServer
import java.io.File
import java.io.IOException
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    val config = if (args.size == 1) {
            try {
                val mapper = ObjectMapper(YAMLFactory())
                mapper.readValue(File(args[0]), Config::class.java)
            } catch (ex: IOException) {
                println("Usage: radar-gateway [config.yml]")
                println("Failed to read config file ${args[0]}: ${ex.message}")
                exitProcess(1)
            }
        } else Config()

    val server = GrizzlyServer(config)
    try {
        server.start()

        println(String.format("Jersey app started on %s.\nHit any key to stop it...",
                config.baseUri))

        System.`in`.read()
    } catch (e: Exception) {
        println("Error starting server: " + e)
    }
    server.shutdown()
}
