package gabfeed_1.com.cyberpointllc.stac.gabfeed.handler;

import gabfeed_1.com.cyberpointllc.stac.gabfeed.model.GabChat;
import gabfeed_1.com.cyberpointllc.stac.gabfeed.model.GabMessage;
import gabfeed_1.com.cyberpointllc.stac.gabfeed.model.GabUser;
import gabfeed_1.com.cyberpointllc.stac.gabfeed.persist.GabDatabase;
import gabfeed_1.com.cyberpointllc.stac.sort.Sorter;
import gabfeed_1.com.cyberpointllc.stac.template.TemplateEngine;
import gabfeed_1.com.cyberpointllc.stac.webserver.WebSession;
import gabfeed_1.com.cyberpointllc.stac.webserver.WebSessionService;
import gabfeed_1.com.cyberpointllc.stac.webserver.WebTemplate;
import gabfeed_1.com.cyberpointllc.stac.webserver.handler.HttpHandlerResponse;
import gabfeed_1.com.cyberpointllc.stac.webserver.handler.MultipartHelper;
import com.sun.net.httpserver.HttpExchange;
import org.apache.commons.lang3.StringUtils;
import plv.colorado.edu.quantmchecker.qual.Inv;
import plv.colorado.edu.quantmchecker.qual.InvTop;
import plv.colorado.edu.quantmchecker.qual.Summary;

import java.net.HttpURLConnection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ChatHandler extends GabHandler {

    private static final String PATH = "/chat/";

    private static final String NEW_CHAT_PREFIX = "new/";

    private final WebTemplate threadTemplate;

    private final WebTemplate messageListTemplate;

    private final WebTemplate messageListTemplateWithoutTime;

    private final WebTemplate newMessageTemplate;

    public ChatHandler(GabDatabase db, WebSessionService webSessionService) {
        super(db, webSessionService);
        threadTemplate = new  WebTemplate("ThreadTemplate.html", getClass());
        messageListTemplate = new  WebTemplate("MessageListSnippet.html", getClass());
        messageListTemplateWithoutTime = new  WebTemplate("MessageListSnippetWithoutTime.html", getClass());
        newMessageTemplate = new  WebTemplate("NewMessageTemplate.html", getClass());
    }

    public static String getPathToChat(String chatId) {
        return PATH + chatId;
    }

    public static String getPathToNewChat(String userId) {
        return PATH + NEW_CHAT_PREFIX + userId;
    }

    @Override
    public String getPath() {
        return PATH;
    }

    @Override
    protected HttpHandlerResponse handleGet(HttpExchange httpExchange, String remainingPath, GabUser user) {
        GabChat gabChat;
        if (remainingPath.startsWith(NEW_CHAT_PREFIX)) {
            GabUser gabUser = getDb().getUser(remainingPath.substring(NEW_CHAT_PREFIX.length()));
            if (gabUser == null) {
                return getErrorResponse(HttpURLConnection.HTTP_BAD_REQUEST, "Invalid User: " + remainingPath);
            }
            if (gabUser.getId().equals(user.getId())) {
                return getErrorResponse(HttpURLConnection.HTTP_BAD_REQUEST, "Cannot chat with yourself: " + remainingPath);
            }
            // First, check and guard against a repeat request
            gabChat = getExistingGabChat(user.getId(), gabUser.getId());
            if (gabChat == null) {
                @Inv({"+<self>=+c74+c75"}) Set<String> userIds = new  LinkedHashSet();
                c74: userIds.add(user.getId());
                c75: userIds.add(gabUser.getId());
                // Create and add a new chat to the database
                gabChat = new  GabChat(getDb(), userIds);
                @Inv("+<self>.chats=+c78") GabDatabase db = getDb();
                c78: db.addChat(gabChat);
            }
        } else {
            gabChat = getDb().getChat(remainingPath);
        }
        if (gabChat == null) {
            return getErrorResponse(HttpURLConnection.HTTP_NOT_FOUND, "Invalid Chat: " + remainingPath);
        }
        @Inv("+<self>.times=+c90") WebSessionService service = getWebSessionService();
        String contents;
        c90: contents = getContents(gabChat, service.getSession(httpExchange));
        String title = "Chat between " + user.getDisplayName() + " and " + gabChat.getOthers(user.getId());
        return getTemplateResponse(title, contents, user);
    }

    private String getContents(GabChat gabChat, WebSession webSession) {
        String messageContents = getMessageContents(gabChat, webSession);
        @Inv("+<self>=+c100+c101+c96+c97") Map<String, String> map = gabChat.getTemplateMap();
        c96: map.put("path", getPath());
        c97: map.put("threadId", gabChat.getId());
        String newMessage = newMessageTemplate.getEngine().replaceTags(map);
        return messageContents + "<hr>" + newMessage;
    }

    private String getMessageContents(GabChat gabChat, WebSession webSession) {
        String suppressTimestampString = webSession.getProperty("suppressTimestamp", "false");
        boolean suppressTimestamp = Boolean.parseBoolean(suppressTimestampString);
        TemplateEngine engine = suppressTimestamp ? messageListTemplateWithoutTime.getEngine() : messageListTemplate.getEngine();
        @Inv({"messages+<self>/(engine.text+engine.text+1)=+c114-c113"}) StringBuilder builder = new  StringBuilder();
        // First, add all existing chat messages
        Sorter sorter = new  Sorter(GabMessage.ASCENDING_COMPARATOR);
        List<GabMessage> messages = sorter.sort(gabChat.getMessages());
        c113: for (GabMessage message : messages) {
            c114: getMessageContentsHelper(message, webSession, engine, builder);
        }
        @Inv("+<self>=+c100+c101+c116") Map<String, String> map = gabChat.getTemplateMap();
        c116: map.put("messages", builder.toString());
        return threadTemplate.getEngine().replaceTags(map);
    }

    @Override
    protected HttpHandlerResponse handlePost(HttpExchange httpExchange, String remainingPath, GabUser user) {
        String query = httpExchange.getRequestURI().getQuery();
        if (!StringUtils.isBlank(query) && query.equals("suppressTimestamp=true")) {
            handlePostHelper(httpExchange);
        }
        GabDatabase db = getDb();
        @Inv("+<self>.messageIds=+c136") GabChat gabChat = db.getChat(remainingPath);
        if (gabChat == null) {
            return getErrorResponse(HttpURLConnection.HTTP_NOT_FOUND, "Invalid Chat: " + remainingPath);
        }
        if (!gabChat.getUserIds().contains(user.getId())) {
            return getErrorResponse(HttpURLConnection.HTTP_BAD_REQUEST, "Only members of this chat can POST to it");
        }
        String messageContent = MultipartHelper.getMultipartFieldContent(httpExchange, "messageContents");
        if (!StringUtils.isBlank(messageContent)) {
            c136: handlePostHelper1(gabChat, messageContent, user);
        }
        return getRedirectResponse(getPathToChat(remainingPath));
    }

    private GabChat getExistingGabChat(String userId1, String userId2) {
        ChatHandlerHelper0 conditionObj0 = new  ChatHandlerHelper0(2);
        for (GabChat gabChat : getDb().getChats(userId1, userId2)) {
            if (gabChat.getUserIds().size() == conditionObj0.getValue()) {
                return gabChat;
            }
        }
        return null;
    }

    private class ChatHandlerHelper0 {

        public ChatHandlerHelper0(int conditionRHS) {
            this.conditionRHS = conditionRHS;
        }

        private int conditionRHS;

        public int getValue() {
            return conditionRHS;
        }
    }

    @Summary({"builder", "1+engine.text*2"})
    private void getMessageContentsHelper(GabMessage message, WebSession webSession, TemplateEngine engine, StringBuilder builder) {
        @Inv("+<self>=+c63+c64+c65+c66+c72+c73+c164") Map<String, String> messageMap = message.getTemplateMap();
        // fix up the contents
        String content = messageMap.get("messageContents");
        c164: messageMap.put("messageContents", PageUtils.formatLongString(content, webSession));
        engine.replaceTagsBuilder(messageMap, builder);
    }

    private void handlePostHelper(HttpExchange httpExchange) {
        @Inv("+<self>.times=+c175") WebSessionService service = getWebSessionService();
        @Inv("+<self>.propertyMap=+c177") WebSession webSession;
        c175: webSession = service.getSession(httpExchange);
        c177: webSession.setProperty("suppressTimestamp", "true");
    }

    @Summary({"gabChat.messageIds", "1"})
    private void handlePostHelper1(GabChat gabChat, String messageContent, GabUser user) {
        gabChat.addMessage(messageContent, user.getId());
    }
}
