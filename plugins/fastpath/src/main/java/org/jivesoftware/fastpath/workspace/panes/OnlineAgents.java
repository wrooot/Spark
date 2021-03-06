/**
 * Copyright (C) 2004-2011 Jive Software. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jivesoftware.fastpath.workspace.panes;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.GridBagLayout;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.Icon;
import javax.swing.JPanel;
import javax.swing.JScrollPane;

import org.jivesoftware.fastpath.FastpathPlugin;
import org.jivesoftware.fastpath.FpRes;
import org.jivesoftware.fastpath.resources.FastpathRes;
import org.jivesoftware.smack.SmackException;
import org.jivesoftware.smack.packet.Presence;
import org.jivesoftware.smackx.workgroup.agent.AgentRoster;
import org.jivesoftware.smackx.workgroup.agent.AgentRosterListener;
import org.jivesoftware.smackx.workgroup.packet.AgentStatus;
import org.jivesoftware.spark.ChatManager;
import org.jivesoftware.spark.PresenceManager;
import org.jivesoftware.spark.SparkManager;
import org.jivesoftware.spark.component.panes.CollapsiblePane;
import org.jivesoftware.spark.component.panes.CollapsiblePaneListener;
import org.jivesoftware.spark.ui.ChatContainer;
import org.jivesoftware.spark.ui.ChatRoom;
import org.jivesoftware.spark.ui.ChatRoomNotFoundException;
import org.jivesoftware.spark.ui.ContactGroup;
import org.jivesoftware.spark.ui.ContactGroupListener;
import org.jivesoftware.spark.ui.ContactItem;
import org.jivesoftware.spark.ui.rooms.ChatRoomImpl;
import org.jivesoftware.spark.util.ModelUtil;
import org.jivesoftware.spark.util.SwingWorker;
import org.jivesoftware.spark.util.log.Log;
import org.jxmpp.jid.BareJid;
import org.jxmpp.jid.EntityBareJid;
import org.jxmpp.jid.parts.Localpart;
import org.jxmpp.jid.parts.Resourcepart;


/**
 * AgentsTable is responsible for managing all agents in the owning workgroup.
 */
public final class OnlineAgents extends JPanel {

	private static final long serialVersionUID = 1L;
	private AgentRoster agentRoster;
    private final ContactGroup contactGroup;

    public OnlineAgents() {
        setLayout(new BorderLayout());

        JPanel topToolbar = new JPanel();
        topToolbar.setLayout(new GridBagLayout());

        topToolbar.setBackground(Color.white);
        setBackground(Color.white);

        final Localpart name = FastpathPlugin.getWorkgroup().getWorkgroupJID().getLocalpartOrThrow();
        final String title = org.jivesoftware.spark.util.StringUtils.makeFirstWordCaptial(name);

        contactGroup = new ContactGroup(FpRes.getString("title.workgroup", title));
        contactGroup.getContainerPanel().add( topToolbar, BorderLayout.NORTH);
        contactGroup.setIcon(FastpathRes.getImageIcon(FastpathRes.FASTPATH_IMAGE_16x16));
        contactGroup.setBackground(Color.white);
        contactGroup.setOpaque(false);
        contactGroup.getListPanel().setBackground(Color.white);
        contactGroup.getTitlePane().setVisible(false);

        contactGroup.addContactGroupListener(new ContactGroupListener() {
            public void contactItemAdded(ContactItem item) {

            }

            public void contactItemRemoved(ContactItem item) {

            }

            public void contactItemDoubleClicked(ContactItem item) {
                Resourcepart resourcepart = Resourcepart.fromOrThrowUnchecked(item.getNickname());
                // TODO: This is a good indicator that
                // - nickname should be of type Resourcepart pretty sure
                // - item.getJid() should possibly return an EntityBareJid
                activateChat(item.getJid().asEntityBareJidOrThrow(), resourcepart);
            }

            public void contactItemClicked(ContactItem item) {

            }

            public void showPopup(MouseEvent e, ContactItem item) {

            }

            public void showPopup(MouseEvent e, Collection<ContactItem> items) {

            }

            public void contactGroupPopup(MouseEvent e, ContactGroup group) {

            }
        });


        final JScrollPane pane = new JScrollPane(contactGroup);
        pane.setBorder(BorderFactory.createEmptyBorder());
        add(pane, BorderLayout.CENTER);

        contactGroup.setCollapsed(false);

        getPane().addCollapsiblePaneListener(new CollapsiblePaneListener() {
            public void paneExpanded() {
                init();
            }

            public void paneCollapsed() {
            }
        });


        init();
    }

    public void init() {
        if (agentRoster != null) {
            return;
        }
        SwingWorker agentWorker = new SwingWorker() {
            Collection<EntityBareJid> agentSet;

            public Object construct() {
                // Initialize Agent Roster
                try
                {
                    agentRoster = FastpathPlugin.getAgentSession().getAgentRoster();
                    agentSet = agentRoster.getAgents();
                    return agentSet;
                }
                catch ( SmackException.NotConnectedException | InterruptedException e )
                {
                    Log.error( "Unable to load agent roster.", e);
                    return null;
                }
            }

            public void finished() {
                final List<EntityBareJid> agentList = new ArrayList<>(agentSet);
                Collections.sort(agentList);

                for ( EntityBareJid agent : agentList )
                {
                    String nickname = SparkManager.getUserManager().getUserNicknameFromJID( agent );
                    if ( nickname == null )
                    {
                        nickname = agent.toString();
                    }

                    ContactItem item = new ContactItem( nickname, nickname, agent )
                    {
                        private static final long serialVersionUID = -8888899031363239813L;

                        public String getToolTipText()
                        {
                            Presence agentPresence = agentRoster.getPresence( agent );
                            return buildTooltip( agentPresence );
                        }
                    };

                    Presence agentPresence = agentRoster.getPresence( agent );
                    item.setPresence( agentPresence );
                }


                agentRoster.addListener(new OnlineAgentListener());
            }
        };

        agentWorker.start();
    }

    public CollapsiblePane getPane() {
        return contactGroup;
    }

    private String buildTooltip(Presence presence) {
        if (!presence.isAvailable()) {
            return FpRes.getString("message.user.not.logged.in");
        }

        AgentStatus agentStatus = presence.getExtension("agent-status", "http://jabber.org/protocol/workgroup");
        List<AgentStatus.ChatInfo> list = agentStatus.getCurrentChats();

        // Add new ones.
        Iterator<AgentStatus.ChatInfo> iter = list.iterator();
        StringBuilder buf = new StringBuilder();
        buf.append("<html>");
        buf.append("<body>");
        buf.append("<table>");

        while (iter.hasNext()) {
            AgentStatus.ChatInfo chatInfo = iter.next();
            Date startDate = chatInfo.getDate();

            String nickname = chatInfo.getUsername();
            if (!ModelUtil.hasLength(nickname)) {
                nickname = FpRes.getString("message.not.specified");
            }

            String question = chatInfo.getQuestion();
            if (!ModelUtil.hasLength(question)) {
                question = "No question asked";
            }

            String email = chatInfo.getEmail();
            if (!ModelUtil.hasLength(email)) {
                email = FpRes.getString("message.not.specified");
            }

            long startTime = startDate.getTime();
            long rightNow = System.currentTimeMillis();
            long totalTime = rightNow - startTime;
            String durationTime = ModelUtil.getTimeFromLong(totalTime);

            buf.append("<tr><td><b><u>Chatting with ").append(nickname).append("</u></b></td></tr>");
            buf.append("<tr><td>Email: ").append(email).append("</td></tr>");
            buf.append("<tr><td>Question: ").append(question).append("</td></tr>");
            buf.append("<tr><td>Chat Duration: ").append(durationTime).append("</td></tr>");
            buf.append("<tr><td><br></td></tr>");
        }

        if (list.size() == 0) {
            buf.append(FpRes.getString("message.agent.is.not.in.chat"));
        }

        buf.append("</table>");
        buf.append("</body>");
        buf.append("</html>");
        return buf.toString();
    }

    /**
     * Activate a chat room with the selected user.
     */
    private void activateChat(final EntityBareJid userJID, final Resourcepart nickname) {
        if (!ModelUtil.hasLength(userJID)) {
            return;
        }

        SwingWorker worker = new SwingWorker() {
            final ChatManager chatManager = SparkManager.getChatManager();
            ChatRoom chatRoom;

            public Object construct() {
                try {
                    Thread.sleep(50);
                }
                catch (InterruptedException e) {
                    Log.error("Error in activate chat.", e);
                }

                ChatContainer chatRooms = chatManager.getChatContainer();

                try {
                    chatRoom = chatRooms.getChatRoom(userJID);
                }
                catch (ChatRoomNotFoundException e) {
                    Log.warning( "Room not found for jid: " + userJID, e );
                }
                return chatRoom;
            }

            public void finished() {
                if (chatRoom == null) {
                    chatRoom = new ChatRoomImpl(userJID, nickname, nickname);
                    chatManager.getChatContainer().addChatRoom(chatRoom);
                }
                chatManager.getChatContainer().activateChatRoom(chatRoom);
            }
        };

        worker.start();

    }

    public class OnlineAgentListener implements AgentRosterListener {
        @Override
        public void agentAdded(final EntityBareJid agent) {
            String nickname = SparkManager.getUserManager().getUserNicknameFromJID(agent);
            if (nickname == null) {
                nickname = agent.toString();
            }

            Presence agentPresence = agentRoster.getPresence(agent);
            if (agentPresence.isAvailable()) {
                ContactItem item = new ContactItem(nickname,nickname, agent) {
					private static final long serialVersionUID = 8080304058990862045L;

					public String getToolTipText() {
                        Presence agentPresence = agentRoster.getPresence(agent);
                        return buildTooltip(agentPresence);
                    }
                };


                item.setPresence(agentPresence);
                contactGroup.addContactItem(item);
            }
        }

        @Override
        public void agentRemoved(EntityBareJid jid) {
            ContactItem item = contactGroup.getContactItemByJID(jid);
            contactGroup.removeContactItem(item);
            contactGroup.fireContactGroupUpdated();
        }

        @Override
        public void presenceChanged(Presence presence) {
            BareJid jid = presence.getFrom().asBareJid();
            ContactItem item = contactGroup.getContactItemByJID(jid);

            if (item != null) {
                item.setPresence(presence);
                if (presence.getType() == Presence.Type.unavailable) {
                    contactGroup.removeContactItem(item);
                }
                else if (presence.getType() == Presence.Type.available) {
                    Icon icon = PresenceManager.getIconFromPresence(presence);
                    if (icon == null) {
                        icon = FastpathRes.getImageIcon(FastpathRes.GREEN_BALL);
                    }
                    item.setIcon(icon);
                }
            }
            else {
                if (presence.getType() == Presence.Type.available) {
                    EntityBareJid agent = presence.getFrom().asEntityBareJidOrThrow();
                    String nickname = SparkManager.getUserManager().getUserNicknameFromJID(agent);
                    if (nickname == null) {
                        nickname = agent.toString();
                    }
                    ContactItem contactItem = new ContactItem(nickname,nickname, presence.getFrom().asBareJid());
                    contactItem.setPresence(presence);
                    contactGroup.addContactItem(contactItem);
                }
            }

            contactGroup.fireContactGroupUpdated();
        }
    }
}
