/**
 * Copyright (C) 2012 Kaj Magnus Lindberg (born 1979)
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
import com.debiki.core.Prelude._
import debiki._
import java.{util => ju}
import play.{api => p}
import play.api.Play.current
import scala.concurrent.Future



abstract class SiteDaoFactory {
  def newSiteDao(siteId: SiteId): SiteDao
}



object SiteDaoFactory {

  /** Creates a non-caching SiteDaoFactory.
    */
  def apply(dbDaoFactory: DbDaoFactory) = new SiteDaoFactory {
    private val _dbDaoFactory = dbDaoFactory

    def newSiteDao(siteId: SiteId): SiteDao = {
      val siteDbDao = _dbDaoFactory.newSiteDbDao(siteId)
      val serializingDbDao = new SerializingSiteDbDao(siteDbDao)
      new NonCachingSiteDao(serializingDbDao, _dbDaoFactory)
    }
  }

}



/** A data access object for site specific data. Data could be loaded
  * from database, or fetched from some in-memory cache.
  *
  * Delegates most requests to SiteDbDao. However, hides some
  * SiteDbDao methods, because calling them directly would mess up
  * the cache in SiteDao's subclass CachingSiteDao.
  *
  * Don't use for more than one http request — it might cache things,
  * in private fields, and is perhaps not thread safe.
  */
abstract class SiteDao
  extends AnyRef
  with AssetBundleDao
  with SettingsDao
  with SpecialContentDao
  with ForumDao
  with CategoriesDao
  with PagesDao
  with PagePathMetaDao
  with PageStuffDao
  with RenderedPageHtmlDao
  with PostsDao
  with UserDao
  with AuditDao {

  def siteDbDao: SiteDbDao
  def dbDao2: DbDao2

  def readWriteTransaction[R](fn: SiteTransaction => R, allowOverQuota: Boolean = false): R =
    dbDao2.readWriteSiteTransaction(siteId, allowOverQuota) { fn(_) }

  def readOnlyTransaction[R](fn: SiteTransaction => R): R =
    dbDao2.readOnlySiteTransaction(siteId, mustBeSerializable = true) { fn(_) }

  def readOnlyTransactionNotSerializable[R](fn: SiteTransaction => R): R =
    dbDao2.readOnlySiteTransaction(siteId, mustBeSerializable = false) { fn(_) }


  // Hack. Here for now only, until forum categories have their own table. [forumcategory]
  // Later on, make protected? Or move to ... where?
  def refreshPageInAnyCache(pageId: PageId) {}


  // ----- Tenant

  def siteId = siteDbDao.siteId

  def loadSite(): Site = siteDbDao.loadTenant()

  @deprecated("use loadSite() instead", "now")
  def loadTenant(): Site = siteDbDao.loadTenant()

  def loadSiteStatus(): SiteStatus =
    siteDbDao.loadSiteStatus()

  def createSite(name: String, hostname: String,
        embeddingSiteUrl: Option[String], pricePlan: Option[String],
        creatorEmailAddress: String, creatorId: UserId, browserIdData: BrowserIdData) : Site = {

    dieIf(hostname contains ":", "DwE3KWFE7")
    val quotaLimitMegabytes = p.Play.configuration.getInt("debiki.newSite.quotaLimitMegabytes")

    readWriteTransaction { transaction =>
      val site = transaction.createSite(name = name, hostname = hostname,
        embeddingSiteUrl, creatorIp = browserIdData.ip, creatorEmailAddress = creatorEmailAddress,
        pricePlan = pricePlan, quotaLimitMegabytes = quotaLimitMegabytes)

      insertAuditLogEntry(AuditLogEntry(
        siteId = this.siteId,
        id = AuditLogEntry.UnassignedId,
        didWhat = AuditLogEntryType.CreateSite,
        doerId = creatorId,
        doneAt = transaction.currentTime,
        browserIdData = browserIdData,
        browserLocation = None,
        targetSiteId = Some(site.id)), transaction)

      site
    }
  }

  def updateSite(changedSite: Site) =
    siteDbDao.updateSite(changedSite)

  def addTenantHost(host: SiteHost) = {
    readWriteTransaction { transaction =>
      transaction.addSiteHost(host)
    }
  }


  // ----- Full text search

  def fullTextSearch(phrase: String, anyRootPageId: Option[PageId]): Future[FullTextSearchResult] =
    siteDbDao.fullTextSearch(phrase, anyRootPageId)


  // ----- List stuff

  def listPagePaths(
        pageRanges: PathRanges,
        include: List[PageStatus],
        orderOffset: PageOrderOffset,
        limit: Int): Seq[PagePathAndMeta] =
    siteDbDao.listPagePaths(pageRanges, include, orderOffset, limit)


  // ----- Notifications

  def saveDeleteNotifications(notifications: Notifications) =
    siteDbDao.saveDeleteNotifications(notifications)

  def loadNotificationsForRole(roleId: RoleId): Seq[Notification] =
    siteDbDao.loadNotificationsForRole(roleId)

  def updateNotificationSkipEmail(notifications: Seq[Notification]): Unit =
    siteDbDao.updateNotificationSkipEmail(notifications)


  // ----- Emails

  def saveUnsentEmail(email: Email): Unit =
    siteDbDao.saveUnsentEmail(email)

  def saveUnsentEmailConnectToNotfs(email: Email, notfs: Seq[Notification]): Unit =
    siteDbDao.saveUnsentEmailConnectToNotfs(email, notfs)

  def updateSentEmail(email: Email): Unit =
    siteDbDao.updateSentEmail(email)

  def loadEmailById(emailId: String): Option[Email] =
    siteDbDao.loadEmailById(emailId)

}



class NonCachingSiteDao(val siteDbDao: SiteDbDao, val dbDaoFactory: DbDaoFactory) extends SiteDao {
  def dbDao2 = dbDaoFactory.newDbDao2()
}
