package btcrenaud.discord.bugreport.service

import btcrenaud.discord.DiscordExtension
import com.typewritermc.engine.paper.logger
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.interactions.commands.build.Commands
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData

/** A bug report system exposed as a Discord slash command. */
data class DiscordBugReportBinding(
    val commandName: String,
    val manifestId: String,
    val adminRoleIds: List<String>,
)

/**
 * Registers and handles Discord slash commands (`/<command> list|status|delete`)
 * for every bug report manifest that opted in.
 */
class DiscordBugReportCommands(
    private val bindings: List<DiscordBugReportBinding>,
) : ListenerAdapter() {

    fun register(guild: Guild) {
        if (bindings.isEmpty()) return
        val commands = bindings.map { binding ->
            Commands.slash(binding.commandName, "Manage bug reports")
                .addSubcommands(
                    SubcommandData("list", "List recent bug reports")
                        .addOption(OptionType.STRING, "player", "Only show reports from this player", false),
                    SubcommandData("status", "Change the status of a report")
                        .addOption(OptionType.STRING, "id", "Report id", true)
                        .addOption(OptionType.STRING, "status", "New status id", true),
                    SubcommandData("delete", "Delete a report")
                        .addOption(OptionType.STRING, "id", "Report id", true),
                )
        }
        guild.updateCommands().addCommands(commands).queue({
            logger.info("Registered ${commands.size} Discord bug report command(s): ${bindings.joinToString { "/${it.commandName}" }}")
        }, { error ->
            logger.warning("Failed to register Discord bug report commands: ${error.message}")
        })
    }

    override fun onSlashCommandInteraction(event: SlashCommandInteractionEvent) {
        val binding = bindings.firstOrNull { it.commandName == event.name } ?: return
        val service = DiscordExtension.bugReportSystem(binding.manifestId)
        if (service == null) {
            event.reply("Bug report system is not available right now.").setEphemeral(true).queue()
            return
        }
        if (!isAllowed(event, binding)) {
            event.reply("You are not allowed to use this command.").setEphemeral(true).queue()
            return
        }

        when (event.subcommandName) {
            "list" -> {
                val filter = event.getOption("player")?.asString
                val lines = service.listReportsPlain(filter)
                event.reply(lines.joinToString("\n").take(2000)).setEphemeral(true).queue()
            }
            "status" -> {
                val id = event.getOption("id")?.asString.orEmpty()
                val status = event.getOption("status")?.asString.orEmpty()
                event.reply(service.updateStatusPlain(id, status).take(2000)).setEphemeral(true).queue()
            }
            "delete" -> {
                val id = event.getOption("id")?.asString.orEmpty()
                event.reply(service.deleteReportPlain(id).take(2000)).setEphemeral(true).queue()
            }
            else -> event.reply("Unknown subcommand.").setEphemeral(true).queue()
        }
    }

    private fun isAllowed(event: SlashCommandInteractionEvent, binding: DiscordBugReportBinding): Boolean {
        val member = event.member ?: return false
        if (binding.adminRoleIds.isEmpty()) {
            return member.hasPermission(Permission.ADMINISTRATOR)
        }
        return member.roles.any { it.id in binding.adminRoleIds }
    }
}
