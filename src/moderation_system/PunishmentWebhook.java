package moderation_system;

import arc.util.Log;
import arc.util.Nullable;
import com.eduardomcb.discord.webhook.WebhookClient;
import com.eduardomcb.discord.webhook.WebhookManager;
import com.eduardomcb.discord.webhook.models.Embed;
import com.eduardomcb.discord.webhook.models.Message;
import mindustry.gen.Player;
import mindustry.net.Administration;

import java.util.ArrayList;
import java.util.Map;

public class PunishmentWebhook {
    private final Administration.Config webhookUrl;
    private WebhookManager webhookManager;

    public PunishmentWebhook() {
        webhookUrl = new Administration.Config("punishment-webhook-url", "The webhook URL for the punishment-logs" +
                " channel.", "", this::reloadUrl);

        if (webhookUrl.string().isEmpty()) {return;}

        this.reloadUrl();
    }

    protected void reloadUrl() {
        webhookManager = new WebhookManager()
                .setListener(new WebhookClient.Callback() {
                    @Override
                    public void onSuccess(String s) {}

                    @Override
                    public void onFailure(int code, String reason) {
                        Log.err("Error code @", code);
                        Log.err(reason);
                    }
                })
                .setChannelUrl(webhookUrl.string());
    }

    public void sendPunishment(String name, String reason, Map<String, String> additionalInformation, Player author,
                               @Nullable PunishmentTarget target, Color color) {
        StringBuilder authorString = new StringBuilder(String.format(
                "### Server\n%s\n### Author\n%s\n### Reason\n%s",
                Administration.Config.serverName.string(),
                author.plainName().replaceFirst("(Admin) ", ""),
                reason
        ));

        additionalInformation.forEach((k, v) -> authorString.append(String.format("\n### %s\n%s", k, v)));

        Embed authorEmbed = new Embed()
                .setTitle(String.format("Punishment: %s", name))
                .setDescription(authorString.toString())
                .setColor(color.value);

        ArrayList<Embed> embeds = new ArrayList<>(1);
        embeds.add(authorEmbed);

        Embed playerEmbed = new Embed()
                .setTitle("Player")
                .setDescription(String.format(
                        "### Name\n%s\n### Colored name\n%s\n### UUID\n%s\n### IP\n%s",
                        target.plainName,
                        target.name,
                        target.uuid,
                        target.ip
                ))
                .setColor(color.value);

        embeds.add(playerEmbed);

        webhookManager.setEmbeds(embeds.toArray(new Embed[0]))
                .setMessage(new Message())
                .exec();
    }

    public void sendPunishment(String name, String reason, Map<String, String> additionalInformation, Player author,
                               @Nullable Player target, Color color) {
        sendPunishment(name, reason, additionalInformation, author, new PunishmentTarget(target), color);
    }

    public void sendPunishment(String name, String reason, Player author, @Nullable Player target, Color color) {
        sendPunishment(name, reason, Map.of(), author, new PunishmentTarget(target), color);
    }

    public void sendPunishment(String name, String reason, Map<String, String> additionalInformation, Player author,
                               @Nullable Administration.PlayerInfo target, Color color) {
        sendPunishment(name, reason, additionalInformation, author, new PunishmentTarget(target), color);
    }

    public void sendPunishment(String name, String reason, Player author, @Nullable Administration.PlayerInfo target, Color color) {
        sendPunishment(name, reason, Map.of(), author, new PunishmentTarget(target), color);
    }
}
