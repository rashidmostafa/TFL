// Conversations: the chats list and 1:1 chats over the transport, with replies, reactions, edits,
// deletes, disappearing and scheduled messages.
plugins {
    id("tfl.android.feature")
}

dependencies {
    implementation(projects.core.transport)
}
