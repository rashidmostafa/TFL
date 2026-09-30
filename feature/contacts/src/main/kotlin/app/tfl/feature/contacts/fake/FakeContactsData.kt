package app.tfl.feature.contacts.fake

/** One member's answer to a request that needs several approvals. Sample data. */
data class PreviewApproval(val name: String, val approved: Boolean, val isYou: Boolean = false)

data class GroupJoinPreview(
    val groupName: String,
    val newMember: String,
    val fingerprint: List<String>,
    val invitedBy: String,
    val receivedMinutesAgo: Int,
    val approvals: List<PreviewApproval>,
)

data class RemoteWipePreview(
    val owner: String,
    val fingerprint: List<String>,
    val lastSeenHoursAgo: Int,
    val approvals: List<PreviewApproval>,
)

/**
 * Sample data: the two preview screens (group joins and remote wipe get real logic in later
 * phases) and the names the debug "add fake friends" tool uses. Replace with repositories then.
 */
internal object FakeContactsData {
    val groupJoin = GroupJoinPreview(
        groupName = "Weekend hikers",
        newMember = "Jonah",
        fingerprint = "3E11A48FCC9277B0D41F9A0B6C2E58D3".chunked(4),
        invitedBy = "Maya",
        receivedMinutesAgo = 18,
        approvals = listOf(
            PreviewApproval("Maya", approved = true),
            PreviewApproval("Sam", approved = true),
            PreviewApproval("Priya", approved = true),
            PreviewApproval("You", approved = false, isYou = true),
        ),
    )

    val remoteWipe = RemoteWipePreview(
        owner = "Sam",
        fingerprint = "88CC014E5F2BA1907D3E62F0B94C1A57".chunked(4),
        lastSeenHoursAgo = 3,
        approvals = listOf(
            PreviewApproval("You", approved = true, isYou = true),
            PreviewApproval("Maya", approved = true),
            PreviewApproval("Priya", approved = false),
        ),
    )

    /** Debug builds: one fake friend per trust state. */
    val debugFriendNames = listOf("Maya Lin", "Kaelen", "Soren K.", "Elena Vance", "Rowan", "Alex")
}
