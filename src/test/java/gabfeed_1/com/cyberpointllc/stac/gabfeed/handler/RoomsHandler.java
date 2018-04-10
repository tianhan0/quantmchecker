package gabfeed_1.com.cyberpointllc.stac.gabfeed.handler;

import gabfeed_1.com.cyberpointllc.stac.gabfeed.model.GabRoom;
import gabfeed_1.com.cyberpointllc.stac.gabfeed.model.GabUser;
import gabfeed_1.com.cyberpointllc.stac.gabfeed.persist.GabDatabase;
import gabfeed_1.com.cyberpointllc.stac.sort.Sorter;
import gabfeed_1.com.cyberpointllc.stac.webserver.WebSessionService;
import gabfeed_1.com.cyberpointllc.stac.webserver.WebTemplate;
import gabfeed_1.com.cyberpointllc.stac.webserver.handler.HttpHandlerResponse;
import com.sun.net.httpserver.HttpExchange;

import java.util.List;

public class RoomsHandler extends GabHandler {

    private final WebTemplate roomDescriptionTemplate;

    public static final String PATH = "/rooms";

    public static final String TITLE = "Rooms";

    public RoomsHandler(GabDatabase db, WebSessionService webSessionService) {
        super(db, webSessionService);
        this.roomDescriptionTemplate = new  WebTemplate("RoomDescriptionListSnippet.html", getClass());
    }

    @Override
    public String getPath() {
        return PATH;
    }

    @Override
    protected HttpHandlerResponse handleGet(HttpExchange httpExchange, String remainingPath, GabUser user) {
        List<GabRoom> rooms = getDb().getRooms();
        // we want them sorted by ID
        Sorter<GabRoom> sorter = new  Sorter(GabRoom.ASCENDING_COMPARATOR);
        rooms = sorter.sort(rooms);
        return getTemplateResponse(TITLE, roomDescriptionTemplate.getEngine().replaceTags(rooms), user);
    }
}
