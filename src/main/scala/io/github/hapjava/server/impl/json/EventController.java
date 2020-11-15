package io.github.hapjava.server.impl.json;

import io.github.hapjava.server.impl.connections.PendingNotification;
import io.github.hapjava.server.impl.http.HttpResponse;
import java.io.ByteArrayOutputStream;
import java.util.Collections;
import javax.json.*;

public class EventController {

  public HttpResponse getMessage(int accessoryId, int iid, JsonValue changed) throws Exception {
    return getMessage(
        Collections.singletonList(new PendingNotification(accessoryId, iid, changed)));
  }

  public HttpResponse getMessage(Iterable<PendingNotification> notifications) throws Exception {
    JsonArrayBuilder characteristics = Json.createArrayBuilder();

    for (PendingNotification notification : notifications) {
      JsonObjectBuilder characteristicBuilder = Json.createObjectBuilder();
      characteristicBuilder.add("aid", notification.aid);
      characteristicBuilder.add("iid", notification.iid);
      characteristicBuilder.add("value", notification.changed);
      characteristics.add(characteristicBuilder.build());
    }

    JsonObject data = Json.createObjectBuilder().add("characteristics", characteristics).build();

    try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
      Json.createWriter(baos).write(data);
      byte[] dataBytes = baos.toByteArray();

      return new EventResponse(dataBytes);
    }
  }
}
