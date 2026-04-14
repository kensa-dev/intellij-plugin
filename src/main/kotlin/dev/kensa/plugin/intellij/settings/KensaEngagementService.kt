package dev.kensa.plugin.intellij.settings

import com.intellij.openapi.components.*

class KensaEngagementState : BaseState() {
    var dismissed by property(false)
    var runsSinceLastPrompt by property(0)
}

@Service(Service.Level.APP)
@State(
    name = "KensaEngagement",
    storages = [Storage("kensa.engagement.xml")]
)
class KensaEngagementService : SimplePersistentStateComponent<KensaEngagementState>(KensaEngagementState())
