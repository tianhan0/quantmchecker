/*
 * Copyright 2006-2014 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jrecruiter.web.actions;

import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.jrecruiter.common.CollectionUtils;
import org.jrecruiter.common.Constants.ServerActions;
import org.jrecruiter.model.Job;
import org.jrecruiter.model.ServerSettings;

import com.sun.syndication.feed.synd.SyndContent;
import com.sun.syndication.feed.synd.SyndContentImpl;
import com.sun.syndication.feed.synd.SyndEntry;
import com.sun.syndication.feed.synd.SyndEntryImpl;
import com.sun.syndication.feed.synd.SyndFeed;
import com.sun.syndication.feed.synd.SyndFeedImpl;
import plv.colorado.edu.quantmchecker.qual.*;

/**
 * @author Gunnar Hillert
 */
public class JobsRssAction extends BaseAction {

	/** serialVersionUID */
	private static final long serialVersionUID = -4901833648423551648L;

	private SyndFeed rssFeed = new SyndFeedImpl();

	private ServerSettings serverSettings;

	/**
	 * @return the rssFeed
	 */
	public SyndFeed getRssFeed() {
		return rssFeed;
	}

	/**
	 * @param rssFeed the rssFeed to set
	 */
	public void setRssFeed(SyndFeed rssFeed) {
		this.rssFeed = rssFeed;
	}

	public String execute() {
		rssFeed.setFeedType("rss_1.0");
		rssFeed.setTitle("AJUG Job Postings");
		rssFeed.setLink("http://www.ajug.org/jobs");
		rssFeed.setDescription("RSS feed of the Atlanta Java User Group's job posting service.");

		@Bound("+ 1 jobs") int i;
		@Inv("= (- entries it) (- c86 c77)") final List <SyndEntry>entries = CollectionUtils.getArrayList();

		@Inv("= sortOrders c70") final Map<String, String>sortOrders = CollectionUtils.getHashMap();
		c70: sortOrders.put("updateDate", "DESC"); //FIXME

		@Iter("<= it jobs") Iterator<Job> it = jobService.getJobs(20, 1, sortOrders, null).iterator();
		Job job;
		while (it.hasNext()) {
			c77: job = it.next();
			final SyndEntry entry = new SyndEntryImpl();
			entry.setTitle(job.getJobTitle());
			entry.setPublishedDate(job.getUpdateDate());

			final SyndContent description = new SyndContentImpl();
			description.setType("text/plain");
			description.setValue(job.getDescription());
			entry.setDescription(description);
			c86: entries.add(entry);

			final String jobUrl = this.serverSettings.getServerAddress() + ServerActions.JOB_DETAIL.getPath() + "?jobId=" + job.getId();

			entry.setLink(jobUrl);
		}

		rssFeed.setEntries(entries);

		return SUCCESS;
	}

	/**
	 * @param serverSettings the serverSettings to set
	 */
	public void setServerSettings(ServerSettings serverSettings) {
		this.serverSettings = serverSettings;
	}


}