package grioanpier.auth.users.bluetoothframework;

import java.util.UUID;

/**
 * A few helpful constants
 */
public class Constants {

    //UUID was acquired from UUID.randomUUID() once and is now hardcoded
    //bluetooth client and server must use the same UUID
    public static final String[] sUUID_STRINGS = {
            "728b4e0c-20bf-47cd-843e-016ab7075f1a",
            "85f8593d-4780-49d6-a174-df5ee4960b4a",
            "f113f31b-d6bc-4bb7-b5da-53f23c155c45",
            "93a5d2e8-4fd2-4415-a245-81a41a4adab7",
            "e3159cf2-b4ae-451d-b30d-ff4c61e86a53",
            "a62e7a9d-fd82-4e10-85ea-2acb45dadc98",
            "d187a344-23c5-4cc2-bc3b-f70eef93b3fc",
            "da5dd52e-1cdf-474d-8eeb-3c31287ab7e2",
            "6b98a8ea-9641-49e8-959c-9a9f767a6809",
            "037c8466-b294-489c-b410-00f5a8c123c9",
    };
    private static UUID[] getUUIDsFromStrings(String[] uuidStrings){
        UUID[] uuids = new UUID[uuidStrings.length];
        for (int i=0; i< uuidStrings.length; i++)
            uuids[i] = UUID.fromString(uuidStrings[i]);
        return uuids;
    }

    public static final UUID[] sUUIDs = getUUIDsFromStrings(sUUID_STRINGS);

    //The available device types
    public static final int DEVICE_SPECTATOR = 0;
    public static final int DEVICE_PLAYER = 1;
    public static final int DEVICE_HOST = 2;

    //ApplicationHelper sources
    public static final String BLUETOOTH_CHAT = "bluetooth_chat";


}
