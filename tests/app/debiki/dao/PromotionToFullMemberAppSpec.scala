/**
 * Copyright (c) 2017 Kaj Magnus Lindberg
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package debiki.dao

import com.debiki.core._
import debiki.{Globals, TextAndHtml}
import java.{util => ju}
import scala.collection.mutable


class PromotionToFullMemberAppSpec extends DaoAppSuite() {
  lazy val dao: SiteDao = Globals.siteDao(Site.FirstSiteId)

  lazy val categoryId: CategoryId =
    dao.createForum("Forum", "/tag-test-forum/",
      Who(owner.id, browserIdData)).uncategorizedCategoryId

  lazy val owner: Member = createPasswordOwner("tag_adm", dao)
  lazy val moderator: Member = createPasswordModerator("tag_mod", dao)
  lazy val member1: Member = createPasswordUser("tag_mb1", dao)
  lazy val member2: Member = createPasswordUser("tag_mb2", dao)
  lazy val member3: Member = createPasswordUser("tag_mb3", dao)
  lazy val wrongMember: Member = createPasswordUser("wr_tg_mbr", dao)

  val pageIds = new mutable.HashMap[Int, PageId]()

  def reply(memberId: UserId, pageNr: Int, text: String, parentNr: Option[PostNr] = None): Post = {
    dao.insertReply(TextAndHtml.testBody(text), pageIds(pageNr),
      replyToPostNrs = Set(parentNr getOrElse PageParts.BodyNr), PostType.Normal,
      Who(memberId, browserIdData), dummySpamRelReqStuff).post
  }

  "The Dao can track user reading progress, and promote" - {
    val now = new ju.Date()

    "someone creates a few pages" in {
      for (pageNr <- 1 to 22) {
        pageIds += pageNr -> createPage(PageRole.Discussion,
          TextAndHtml.testTitle(s"Page nr $pageNr"), TextAndHtml.testBody(s"Page nr $pageNr body"),
          owner.id, browserIdData, dao, Some(categoryId))
      }
    }

    "someone else posts replies to the first 5 pages" in {
      for (pageNr <- 1 to 5) {
        for (replyNr <- 1 to 25) {
          val postNoTags = reply(moderator.id, pageNr, s"Reply $replyNr, page nr $pageNr")
        }
      }
    }

    "a member starts at trust level New" in {
      member1.effectiveTrustLevel mustBe TrustLevel.New
      member1.trustLevel mustBe TrustLevel.New
      member1.lockedThreatLevel mustBe None
    }

    "a member reads a tiny bit, won't get promoted yet" in {
      dao.trackReadingProgressPerhapsPromote(member1, pageIds(1), ReadingProgress(
        firstVisitedAt = startTime.minusSeconds(10),
        lastVisitedAt = startTime,
        lastViewedPostNr = 1,
        lastReadAt = Some(startTime),
        lastPostNrsReadRecentFirst = Vector.empty,
        lowPostNrsRead = Set[PostNr](1),
        secondsReading = 60))

      // Didn't get promoted
      val (member1After, stats) = loadTheMemberAndStats(member1.id)(dao)
      member1After.effectiveTrustLevel mustBe TrustLevel.New
      member1After.trustLevel mustBe TrustLevel.New
      member1After.lockedThreatLevel mustBe None
    }

    "a member reads a bit more, almost gets promoted" in {
      playTime(1000)
      dao.trackReadingProgressPerhapsPromote(member1, pageIds(2), ReadingProgress(
        firstVisitedAt = currentTime.minusSeconds(1),
        lastVisitedAt = currentTime,
        lastViewedPostNr = 1,
        lastReadAt = Some(currentTime),
        lastPostNrsReadRecentFirst = Vector.empty,
        lowPostNrsRead = Set[PostNr](1 to 25: _*),  // 24 replies, 25 = the limit
        secondsReading = 6*60))

      // Entering the 3rd topic, entering 4 = the limit
      dao.trackReadingProgressPerhapsPromote(member1, pageIds(3), ReadingProgress(
        firstVisitedAt = currentTime.minusSeconds(1),
        lastVisitedAt = currentTime,
        lastViewedPostNr = 1,
        lastReadAt = Some(currentTime),
        lastPostNrsReadRecentFirst = Vector.empty,
        lowPostNrsRead = Set[PostNr](1),
        secondsReading = 59))   // 60 + 6*60 + 59 = 7 min 59 seconds, limit = 8 minutes

      // Didn't get promoted now either.
      val (member1After, stats) = loadTheMemberAndStats(member1.id)(dao)
      member1After.effectiveTrustLevel mustBe TrustLevel.New
      member1After.trustLevel mustBe TrustLevel.New
      member1After.lockedThreatLevel mustBe None
      stats.numSecondsReading mustBe (8*60 - 1)
      stats.numDiscourseTopicsEntered mustBe 3
      stats.numDiscourseRepliesRead mustBe 24
    }

    "reads a tiny bit more, finally gets promoted to Basic" in {
      playTime(1000)
      dao.trackReadingProgressPerhapsPromote(member1, pageIds(4), ReadingProgress(
        firstVisitedAt = currentTime.minusSeconds(1),
        lastVisitedAt = currentTime,
        lastViewedPostNr = 1,
        lastReadAt = Some(currentTime),
        lastPostNrsReadRecentFirst = Vector.empty,
        lowPostNrsRead = Set[PostNr](1, 2), // means 1 more reply read (orig post = 1 doesn't count)
        secondsReading = 1))

      // Did get promoted.
      val (member1After, stats) = loadTheMemberAndStats(member1.id)(dao)
      member1After.effectiveTrustLevel mustBe TrustLevel.Basic
      member1After.trustLevel mustBe TrustLevel.Basic
      member1After.lockedThreatLevel mustBe None
      stats.numSecondsReading mustBe (8*60)
      stats.numDiscourseTopicsEntered mustBe 4
      stats.numDiscourseRepliesRead mustBe 25
    }

    "reads a lot more, during many days, enough for becoming a Full Member" in {
      pending
    }

    "gets and casts a Like vote, then gets promoted" in {
      pending
    }
  }
}
