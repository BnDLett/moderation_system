package moderation_system;

import arc.util.Log;
import com.eduardomcb.discord.webhook.WebhookClient;
import com.eduardomcb.discord.webhook.WebhookManager;
import com.eduardomcb.discord.webhook.models.Embed;
import com.eduardomcb.discord.webhook.models.Message;
import mindustry.net.Administration;

public class PunishmentWebhook {
    private final Administration.Config webhookUrl;
    private final WebhookManager webhookManager;

    public PunishmentWebhook() {
        webhookUrl = new Administration.Config("punishment-webhook-url", "The webhook URL for the punishment-logs" +
                " channel.", "", this::reloadUrl);
        webhookManager = new WebhookManager();
//        webhookManager.setListener(new WebhookClient.Callback() {
//            @Override
//            public void onSuccess(String s) {}
//
//            @Override
//            public void onFailure(int code, String reason) {
//                Log.err("Error code @", code);
//                Log.err(reason);
//            }
//        });

        if (webhookUrl.string().isEmpty()) {return;}

        this.reloadUrl();
    }

    protected void reloadUrl() {
        webhookManager.setChannelUrl(webhookUrl.string());
    }

    public void sendPunishment(String name, String reason) {
//        Embed embed = new Embed();
//        embed.setTitle(name);
//        embed.setDescription(reason);

        Message message = new Message();
        message.setContent("me when i");
        message.setUsername("phos");

//        webhookManager.setEmbeds(new Embed[] {});
        webhookManager.setMessage(message);
        webhookManager.exec();
    }
}
