import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import org.radarcns.gateway.Config
import org.radarcns.gateway.GrizzlyServer
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.system.exitProcess

fun loadConfig(args: Array<String>): Config {
    val configFileName = when {
        args.size == 1 -> args[0]
        Files.exists(Paths.get("gateway.yml")) -> "gateway.yml"
        else -> null
    }
    return if (configFileName != null) {
        val configFile = File(configFileName)
        println("Reading configuration from ${configFile.absolutePath}")
        try {
            val mapper = ObjectMapper(YAMLFactory())
            mapper.readValue(configFile, Config::class.java)
        } catch (ex: IOException) {
            println("Usage: radar-gateway [config.yml]")
            println("Failed to read config file $configFile: ${ex.message}")
            exitProcess(1)
        }
    } else Config()
}

fun main(args: Array<String>) {
    val config = loadConfig(args)

    val server = GrizzlyServer(config)
    try {
        server.start()

        println(String.format("Jersey app started on %s.\nHit any key to stop it...",
                config.baseUri))

        System.`in`.read()
    } catch (e: Exception) {
        println("Error starting server: $e")
    }
    server.shutdown()
}
