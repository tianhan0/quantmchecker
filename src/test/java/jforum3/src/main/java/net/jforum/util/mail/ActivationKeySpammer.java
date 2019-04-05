/*
 * Copyright (c) JForum Team
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms,
 * with or without modification, are permitted provided
 * that the following conditions are met:
 *
 * 1) Redistributions of source code must retain the above
 * copyright notice, this list of conditions and the
 * following  disclaimer.
 * 2)  Redistributions in binary form must reproduce the
 * above copyright notice, this list of conditions and
 * the following disclaimer in the documentation and/or
 * other materials provided with the distribution.
 * 3) Neither the name of "Rafael Steil" nor
 * the names of its contributors may be used to endorse
 * or promote products derived from this software without
 * specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT
 * HOLDERS AND CONTRIBUTORS "AS IS" AND ANY
 * EXPRESS OR IMPLIED WARRANTIES, INCLUDING,
 * BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR
 * PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL
 * THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA,
 * OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER
 * IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN
 * ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
 * ADVISED OF THE POSSIBILITY OF SUCH DAMAGE
 *
 * The JForum Project
 * http://www.jforum.net
 */
package net.jforum.util.mail;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.jforum.core.exceptions.MailException;
import net.jforum.entities.User;
import net.jforum.util.ConfigKeys;
import net.jforum.util.JForumConfig;
import plv.colorado.edu.quantmchecker.qual.Bound;
import plv.colorado.edu.quantmchecker.qual.Inv;


/**
 * Send an email for account activation
 *
 * @author Rafael Steil
 */
public class ActivationKeySpammer extends Spammer {
	public ActivationKeySpammer(JForumConfig config) throws MailException {
		super(config);
	}

	public void prepare(User user) {
		@Bound("13") int i;
		@Inv("= sb (+ c67 c68 c69 c70 c71 c72)") StringBuilder sb = new StringBuilder();
		c67: sb.append(this.buildForumLink());
		c68: sb.append("user/activateAccount/");
		c69: sb.append(user.getActivationKey());
		c70: sb.append('/');
		c71: sb.append(user.getId());
		c72: sb.append(this.getConfig().getValue(ConfigKeys.SERVLET_EXTENSION));
		String url = sb.toString();

		@Inv("= manualSb (+ c76 c77 c78)") StringBuilder manualSb = new StringBuilder();
		c76: manualSb.append(this.buildForumLink());
		c77: manualSb.append("user/activateManually");
		c78: manualSb.append(this.getConfig().getValue(ConfigKeys.SERVLET_EXTENSION));
		String manualUrl = manualSb.toString();

		@Inv("= params (+ c82 c83 c84)") Map<String, Object> params = new HashMap<String, Object>();
		c82: params.put("url", url);
		c83: params.put("user", user);
		c84: params.put("manualUrl", manualUrl);

		@Inv("= recipients c87") List<User> recipients = new ArrayList<User>();
		c87: recipients.add(user);

		this.setUsers(recipients);
		this.setTemplateParams(params);

		this.prepareMessage(this.getConfig().getValue(ConfigKeys.MAIL_ACTIVATION_KEY_SUBJECT),
			this.getConfig().getValue(ConfigKeys.MAIL_ACTIVATION_KEY_MESSAGE_FILE));
	}
}