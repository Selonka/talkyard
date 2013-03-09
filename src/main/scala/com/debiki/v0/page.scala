// vim: fdm=marker et ts=2 sw=2 tw=80 fo=tcqwn list

package com.debiki.v0

import java.{util => ju}
import collection.{immutable => imm, mutable => mut}
import Prelude._
import Page._


// Preparing to rename Debate to Page:
object Page {


  val TitleId = "0t"
  val BodyId = "0b"
  val ConfigPostId = "0c"


  def isArticleOrConfigPostId(id: String) =
    id == Page.BodyId || id == Page.TitleId || id == Page.ConfigPostId


  def isReply(action: PostActionDtoOld): Boolean = action match {
    case post: CreatePostAction if !isArticleOrConfigPostId(post.id) => true
    case _ => false
  }


  def fromActions(guid: String, people: People, actions: List[AnyRef]): Debate =
    Debate(guid, people) ++ actions


  /** Assigns ids to actions and updates references from e.g. Edits to Posts.
   *  Only remaps IDs that start with "?".
   */
  def assignIdsTo[T <: PostActionDtoOld](actionsToRemap: List[T], nextNewReplyId: Int): List[T] = {
    val remaps = mut.Map[String, String]()
    var nextReplyId = nextNewReplyId

    // Generate new ids.
    actionsToRemap foreach { a: T =>
      require(!remaps.contains(a.id)) // each action must be remapped only once
      remaps(a.id) =
          if (a.id.head == '?') {
            if (Page.isReply(a)) {
              // Reply ids should be small consecutive numbers that can be used
              // as indexes into a bitset. That's why they're allocated in
              // this manner.
              nextReplyId += 1
              intToReplyId(nextReplyId - 1)
            }
            else {
              nextRandomString()
            }
          }
          else {
            assert(Page.isArticleOrConfigPostId(a.id))
            a.id
          }
    }

    // Remap ids and update references to ids.
    def rmpd(id: String) = remaps.getOrElse(id, id)
    def updateIds(action: T): T = (action match {
      case p: CreatePostAction => p.copy(id = remaps(p.id), // postId == id
        parent = rmpd(p.parent))
      case r: Rating => r.copy(id = remaps(r.id), postId = rmpd(r.postId))
      case f: Flag => f.copy(id = remaps(f.id), postId = rmpd(f.postId))
      case e: Edit => e.copy(id = remaps(e.id), postId = rmpd(e.postId))
      case a: EditApp => a.copy(id = remaps(a.id), editId = rmpd(a.editId),
        postId = rmpd(a.postId))
      case d: Delete => d.copy(id = remaps(d.id), postId = rmpd(d.postId))
      case r: ReviewPostAction => r.copy(id = remaps(r.id), postId = rmpd(r.postId))
      case a: PostActionDto => a.copy(id = remaps(a.id), postId = rmpd(a.postId))
      case x => assErr("DwE3RSEK9")
    }).asInstanceOf[T]

    val actionsRemapped: List[T] = actionsToRemap map updateIds
    actionsRemapped
  }


  val ReplyIdAlphabet =
    "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz"

  val ReplyIdAlphabetBase = ReplyIdAlphabet.length

  assert(ReplyIdAlphabetBase == 62)


  /**
   * Converts a number to a reply id, namely a base-62 string. Examples:
   * 1 -> "1"  10 -> "A"  11 -> "B"  61 -> "z"
   * 64 -> "12", 72 -> "1A"
   *
   * (An alternative is to reverse the digits, which would tend to result in more
   * well balanced binary trees (assuming ids used as keys). This would, however
   * result in related replies being located "randomly" (withing one page) when
   * stored in a database. Therefore, it'd usually be more work to load only parts
   * of a page? And it's better store replies sorted by time (not reverse digits)?)
   */
  def intToReplyId(value: Int): String = {
    var remaining = value
    var replyId = ""
    while (remaining > 0) {
      val remainder = remaining % 62
      remaining = remaining / 62
      replyId += ReplyIdAlphabet.charAt(remainder)
    }
    replyId.reverse
  }


  def replyIdToInt(replyId: String): Int = {
    var multiplier = 1
    replyId.foldRight(0) { (sum, ch) =>
      val value = ch match {
        case num if '0' <= ch && ch <= '9' => ch - '0'
        case upper if 'A' <= ch && ch <= 'Z' => ch - 'A' + 10
        case lower if 'a' <= ch && ch <= 'z' => ch - 'a' + 10 + 26
        case x => assErr("DwE15GW08", s"Bad reply id, cannot convert to int: `$replyId'")
      }
      val newSum = sum + value * multiplier
      multiplier *= ReplyIdAlphabetBase
      newSum
    }
  }

}


/** Wraps PostActionDto:s in PostActions and groups them by post id.
  *
  * That is, for each PostActionDto, wraps it in a PostAction, and people
  * can then use the PostAction, instead of the rather non-user-friendly
  * PostActionDto (data-transfer-object).
  */
abstract class PostActionsWrapper { self: Debate =>


  def actionDtos: List[PostActionDto]


  def getActionById(id: String): Option[PostAction] =
    actionsById.get(id)


  def getActionsByPostId(postId: String): List[PostAction] =
    actionsByPostId(postId)


  // Maps data-transfer-objects to PostAction:s. Never mutated once constructed.
  private lazy val (
    actionsById,
    actionsByPostId): (mut.Map[String, PostAction], mut.Map[String, List[PostAction]]) = {

    var actionsById = mut.Map[String, PostAction]()
    var actionsByPostId = mut.Map[String, List[PostAction]]().withDefaultValue(Nil)

    for (actionDto <- actionDtos) {
      val action = actionDto.payload match {
        // case _ => CreatePostAction => Post(this.asInstanceOf[Debate], action)
        // case _ => EditPostAction => Patch(this.asInstanceOf[Debate], action)
        case _ => new PostAction(this.asInstanceOf[Debate], actionDto)
      }
      actionsById(action.id) = action
      val otherActions = actionsByPostId(action.postId)
      if (otherActions.find(_.id == action.id).isEmpty)
        actionsByPostId(actionDto.postId) = action :: otherActions
    }

    (actionsById, actionsByPostId)
  }

}



/** A page. It is constructed via actions (the Action/Command design pattern
  * that create posts (e.g. title, body, comments), edit them, review, close, delete,
  * etcetera.
  *
  * @param guid The page's id, unique per website.
  * @param people People who has contributed to the page. If some people are missing,
  * certain functions might fail (e.g. a function that fetches the name of the author
  * of the page body). — This class never fetches anything lazily from database.
  * @param posts And all other params, except for `actions` are ... deprecated? And
  * should instead be merged into `actions`.
  * @param actionDtos The actions that build up the page.
  */
// Could rename to Page,... no, PageActions or *PageParts*?
case class Debate (
  guid: String,  // COULD rename to pageId?
  people: People = People.None,
  posts: List[CreatePostAction] = Nil,
  ratings: List[Rating] = Nil,
  edits: List[Edit] = Nil,
  editApps: List[EditApp] = Nil,
  flags: List[Flag] = Nil,
  deletions: List[Delete] = Nil,
  reviews: List[ReviewPostAction] = Nil,
  actionDtos : List[PostActionDto] = Nil) extends PostActionsWrapper {

  private lazy val postsById =
      imm.Map[String, CreatePostAction](posts.map(x => (x.id, x)): _*)

  def actionCount: Int =
     posts.size + ratings.size + edits.size + editApps.size +
     flags.size + deletions.size + actionDtos.size

  def allActions: Seq[PostActionDtoOld] =
     deletions:::flags:::editApps:::edits:::ratings:::posts:::actionDtos

  // Try to remove/rewrite? Doesn't return e.g Post or Patch.
  def smart(action: PostActionDtoOld) = new PostActionOld(this, action)

  // For the love of god, I have to rename this crap, this file is a mess.
  // Fixed (soon). Rewrite to PostActionDto and use PostActionsWrapper.
  def getSmart(actionId: String): Option[PostActionOld] = {
    postsById.get(actionId).map(new Post(this, _)) orElse
      rating(actionId).map(new PostActionOld(this, _)) orElse
      editsById.get(actionId).map(new Patch(this, _)) orElse
      editApp(actionId).map(new PostActionOld(this, _)) orElse
      flagsById.get(actionId).map(new PostActionOld(this, _)) orElse
      deletionsById.get(actionId).map(new PostActionOld(this, _)) orElse
      reviewsById.get(actionId).map(new Review(this, _))
  }

  lazy val (
      postsByParentId: imm.Map[String, List[CreatePostAction]]
        ) = {

    // Add id -> post mappings to mutable multimaps.
    var postMap = mut.Map[String, mut.Set[CreatePostAction]]()
    for (p <- posts) {
      val mmap = postMap
      // =  p.tyype match {
      //  case PostType.Text => postMap
      // }
      mmap.getOrElse(
        p.parent, { val s = mut.Set[CreatePostAction](); mmap.put(p.parent, s); s }) += p
    }

    // Copy to immutable versions.
    def buildImmMap(mutMap: mut.Map[String, mut.Set[CreatePostAction]]
                       ): imm.Map[String, List[CreatePostAction]] = {
      // COULD sort the list in ascenting ctime order?
      // Then list.head would be e.g. the oldest title -- other code
      // assume posts ase sorted in this way?
      // See Post.templatePost, titlePost.
      imm.Map[String, List[CreatePostAction]](
        (for ((parentId, postsSet) <- mutMap)
        yield (parentId, postsSet.toList // <-- sort this list by ctime asc?
              )).toList: _*).withDefaultValue(Nil)
    }

    val immPostMap = buildImmMap(postMap)
    immPostMap
  }


  private class _RatingsOnActionImpl extends RatingsOnAction {
    val _mostRecentByUserId = mut.Map[String, Rating]()
    val _mostRecentByNonAuLoginId = mut.Map[String, Rating]()
    val _allRecentByNonAuIp =
      mut.Map[String, List[Rating]]().withDefaultValue(Nil)

    override def mostRecentByUserId: collection.Map[String, Rating] =
      _mostRecentByUserId

    override lazy val mostRecentByNonAuLoginId: collection.Map[String, Rating] =
      _mostRecentByNonAuLoginId

    override lazy val allRecentByNonAuIp: collection.Map[String, List[Rating]] =
      _allRecentByNonAuIp

    override def curVersionOf(rating: Rating): Rating = {
      val user = smart(rating).user_!
      val curVer = user.isAuthenticated match {
        case true => _mostRecentByUserId(user.id)
        case false => _mostRecentByNonAuLoginId(rating.loginId)
      }
      assert(rating.ctime.getTime <= curVer.ctime.getTime)
      assert(rating.postId == curVer.postId)
      curVer
    }
  }

  // Analyze ratings, per action.
  // (Never change this mut.Map once it's been constructed.)
  private lazy val _ratingsByActionId: mut.Map[String, _RatingsOnActionImpl] = {
    val mutRatsByPostId =
      mut.Map[String, _RatingsOnActionImpl]()

    // Remember the most recent ratings per user and non-authenticated login id.
    for (rating <- ratings) {
      var singlePostRats = mutRatsByPostId.getOrElseUpdate(
        rating.postId, new _RatingsOnActionImpl)
      val user = smart(rating).user_!
      val (recentRatsMap, key) = user.isAuthenticated match {
        case true => (singlePostRats._mostRecentByUserId, user.id)
        case false => (singlePostRats._mostRecentByNonAuLoginId, rating.loginId)
      }

      val perhapsOtherRating = recentRatsMap.getOrElseUpdate(key, rating)
      if (perhapsOtherRating.ctime.getTime < rating.ctime.getTime) {
        // Different ctime, must be different ratings
        assert(perhapsOtherRating.id != rating.id)
        // But by the same login, or user
        assert(perhapsOtherRating.loginId == rating.loginId ||
           smart(perhapsOtherRating).user.map(_.id) ==
              smart(rating).user.map(_.id))
        // Keep the most recent rating only.
        recentRatsMap(key) = rating
      }
    }

    // Remember all unauthenticated ratings, per IP.
    // This cannot be done until the most recent ratings by each non-authn
    // user has been found (in the for loop just above).
    for {
      singleActionRats <- mutRatsByPostId.values
      nonAuRating <- singleActionRats._mostRecentByNonAuLoginId.values
    } {
      val byIp = singleActionRats._allRecentByNonAuIp
      val ip = smart(nonAuRating).ip_!
      val otherRatsSameIp = byIp(ip)
      byIp(ip) = nonAuRating :: otherRatsSameIp
    }

    mutRatsByPostId
  }


  /** The guid prefixed with a dash.
   *
   * A debate page can be identified either by "-guid"
   * or "/path/to/page/".
   */
  def guidd = "-"+ guid

  // Use these instead.
  def id = guid
  def idd = guidd
  def pageId = id  // when/if I rename Debate to PageActions.

  def body: Option[Post] = vipo(Page.BodyId)

  def body_! = vipo_!(Page.BodyId)

  def bodyText: Option[String] = vipo(Page.BodyId).map(_.text)


  def title_! = vipo_!(Page.TitleId)

  /** The page title if any. */
  def title = titlePost // COULD remove `titlePost`
  def titlePost: Option[Post] = vipo(Page.TitleId)

  /** The page title, as plain text, but the empty string is changed to None. */
  def titleText: Option[String] = titlePost.map(_.text).filter(_.nonEmpty)

  /** The page title, as XML. */
  //def titleXml: Option[xml.Node] = body.flatMap(_.titleXml)

  /** A Post with template engine source code, for the whole page. */
  def pageConfigPost: Option[Post] = vipo(Page.ConfigPostId)


  // -------- Ratings

  private lazy val ratingsById: imm.Map[String, Rating] =
    imm.Map[String, Rating](ratings.map(x => (x.id, x)): _*)

  def rating(id: String): Option[Rating] = ratingsById.get(id)

  def ratingsByActionId(actionId: String): Option[RatingsOnAction] =
    _ratingsByActionId.get(actionId)

  def ratingsByUser(withId: String): Seq[Rating] =
    ratings.filter(smart(_).identity.map(_.userId) == Some(withId))


  // ====== Older stuff below (everything in use though!) ======


  // -------- Posts

  def postCount = posts.length

  def post(id: String): Option[CreatePostAction] = postsById.get(id)

  def vipo_!(postId: String): Post =  // COULD rename to post_!(withId = ...)
    vipo(postId).getOrElse(runErr(
      "DwE3kR49", "Post not found: "+ safed(postId)))

  def vipo(postId: String): Option[Post] = // COULD rename to post(withId =...)
    post(postId).map(new Post(this, _))

  def postsByUser(withId: String): Seq[CreatePostAction] =
    posts.filter(smart(_).identity.map(_.userId) == Some(withId))

  lazy val (
      numPosters,
      numPostsDeleted,
      numRepliesVisible,
      numPostsToReview,
      lastVisiblePostDati) = {
    var numDeleted = 0
    var numVisible = 0
    var numPendingReview = 0
    var lastDati: Option[ju.Date] = None
    var posterUserIds = mut.Set[String]()
    for (post <- vipos_!) {
      if (post.isDeleted) numDeleted += 1
      else if (post.someVersionApproved) {
        // posterUserIds.add(post.user_!.id) — breaks, users sometimes absent.
        // Wait until I've added DW1_PAGE_ACTIONS.USER_ID?
        if (Page.isReply(post.action)) {
          numVisible += 1
        }
        else {
          // Ignore. We don't count the page body or title — it's rather uninteresting
          // to count them because they always exist (on normal pages) Num replies,
          // however, is interesting.
        }
        val isNewer = lastDati.isEmpty || lastDati.get.getTime < post.creationDati.getTime
        if (isNewer) lastDati = Some(post.creationDati)
      }
      else numPendingReview += 1
    }
    (posterUserIds.size, numDeleted, numVisible, numPendingReview, lastDati)
  }


  private def vipos_! : List[Post] = // rename!
    posts.map(post => vipo_!(post.id))


  // -------- Replies

  def repliesTo(id: String): List[CreatePostAction] =
    postsByParentId.getOrElse(id, Nil).filterNot(_.id == id)

  def successorsTo(postId: String): List[CreatePostAction] = {
    val res = repliesTo(postId)
    res.flatMap(r => successorsTo(r.id)) ::: res
  }


  // -------- Edits

  def vied_!(editId: String): Patch =
    vied(editId).getOrElse(assErr("DwE03ke1"))

  def vied(editId: String): Option[Patch] =
    editsById.get(editId).map(new Patch(this, _))

  lazy val editsById: imm.Map[String, Edit] = {
    val m = edits.groupBy(_.id)
    m.mapValues(list => {
      runErrIf3(list.tail.nonEmpty,
        "DwE9ksE53", "Two ore more Edit:s with this id: "+ list.head.id)
      list.head
    })
  }

  def editAppsByEdit(id: String) = _editAppsByEditId.getOrElse(id, Nil)

  private lazy val _editAppsByEditId: imm.Map[String, List[EditApp]] = {
    editApps.groupBy(_.editId)
    // Skip this List --> head conversion. There might be > 1 app per edit,
    // since apps can be deleted -- then the edit can be applied again later.
    //m.mapValues(list => {
    //  errorIf(list.tail.nonEmpty, "Two ore more EditApps with "+
    //          "same edit id: "+ list.head.editId)
    //  list.head
    //})
  }

  private lazy val editsByPostId: imm.Map[String, List[Edit]] =
    edits.groupBy(_.postId)

  private lazy val editAppsByPostId: imm.Map[String, List[EditApp]] =
    editApps.groupBy(ea => editsById(ea.editId).postId)

  def editsFor(postId: String): List[Patch] =
    editsByPostId.getOrElse(postId, Nil) map (new Patch(this, _))

  /** Edits applied to the specified post, sorted by most-recent first.
   */
  def editAppsTo(postId: String): List[EditApp] =
    // The list is probably already sorted, since new EditApp:s are
    // prefixed to the editApps list.
    editAppsByPostId.getOrElse(postId, Nil).sortBy(- _.ctime.getTime)

  def editApp(withId: String): Option[EditApp] =
    editApps.filter(_.id == withId).headOption


  // -------- Flags

  private lazy val flagsById: imm.Map[String, Flag] =
    imm.Map[String, Flag](flags.map(x => (x.id, x)): _*)


  // -------- Deletions

  private lazy val deletionsById: imm.Map[String, Delete] =
    imm.Map[String, Delete](deletions.map(x => (x.id, x)): _*)

  /** If actionId was explicitly deleted (not indirectly, via
   *  wholeTree/recursively = true).
   */
  def deletionFor(actionId: String): Option[Delete] =
    deletions.find(_.postId == actionId).headOption
    // COULD check if the deletion itself was deleted!?
    // That is, if the deletion was *undone*. Delete, undo, redo... a redo
    // would be a deleted deletion of a deletion?

  def deletion(withId: String): Option[Delete] =
    deletions.filter(_.id == withId).headOption


  // -------- Reviews (i.e. approvals and rejections)

  private lazy val reviewsById: imm.Map[String, ReviewPostAction] =
    imm.Map[String, ReviewPostAction](reviews.map(x => (x.id, x)): _*)

  private lazy val reviewsByTargetId: imm.Map[String, List[ReviewPostAction]] =
    reviews.groupBy(_.postId)

  def getReview(id: String): Option[Review] =
    reviewsById.get(id) map (new Review(this, _))

  def explicitReviewsOf(actionId: String): List[ReviewPostAction] =
    reviewsByTargetId.getOrElse(actionId, Nil)

  def explicitReviewsOf(action: PostActionDtoOld): List[ReviewPostAction] =
    explicitReviewsOf(action.id)


  // -------- Construction

  def + (post: CreatePostAction): Debate = copy(posts = post :: posts)
  def + (rating: Rating): Debate = copy(ratings = rating :: ratings)
  def + (edit: Edit): Debate = copy(edits = edit :: edits)
  def + (editApp: EditApp): Debate = copy(editApps = editApp :: editApps)
  def + (flag: Flag): Debate = copy(flags = flag :: flags)
  def + (deletion: Delete): Debate = copy(deletions = deletion :: deletions)
  def + (review: ReviewPostAction): Debate = copy(reviews = review :: reviews)
  def + (action: PostActionDto): Debate = copy(actionDtos = action :: actionDtos)

  // Could try not to add stuff that's already included in this.people.
  def ++(people: People): Debate = this.copy(people = this.people ++ people)

  def ++(page: Debate): Debate = this ++ page.allActions

  // COULD [T <: Action] instead of >: AnyRef?
  def ++[T >: AnyRef] (actions: Seq[T]): Debate = {
    var posts2 = posts
    var ratings2 = ratings
    var edits2 = edits
    var editApps2 = editApps
    var flags2 = flags
    var dels2 = deletions
    var reviews2 = reviews
    var actions2 = this.actionDtos
    for (a <- actions) a match {
      case p: CreatePostAction => posts2 ::= p
      case r: Rating => ratings2 ::= r
      case e: Edit => edits2 ::= e
      case a: EditApp => editApps2 ::= a
      case f: Flag => flags2 ::= f
      case d: Delete => dels2 ::= d
      case r: ReviewPostAction => reviews2 ::= r
      case a: PostActionDto => actions2 ::= a
      case x => runErr(
        "DwE8k3EC", "Unknown action type: "+ classNameOf(x))
    }
    Debate(id, people, posts2, ratings2,
        edits2, editApps2, flags2, dels2, reviews2, actions2)
  }


  // -------- Partition by version

  /**
   * Returns a Page with all non-approved stuff removed.
   */
  def approvedVersion: Debate =
    splitByVersion(PageVersion.LatestApproved).desired

  /**
   * Partitions this page into two or three parts:
   * 1) The `desired` part, which contains everything that happened
   *    up to and including `pageVersion`, and, if pageVersion.approved,
   *    only things that have been approved.
   * 2) The `inclUnapproved` part, which contains everything that happened
   *    up to and including `pageVersion.dati`, including unapproved stuff.
   * 3) The `inclTooRecent` part which is the whole page unchanged (both
   *    too recent and unapproved stuff).
   *
   * However when partitioning on Post approval date, only
   * Posts, Edits and EditApps are partitioned; everything else is
   * included in all of 1, 2, and 3 above.
   */
  def splitByVersion(pageVersion: PageVersion): PageSplitByVersion = {

    val pageUpToAndInclDati = splitByTime(pageVersion.dati)

    if (!pageVersion.approved)
      return PageSplitByVersion(
        desired = pageUpToAndInclDati,
        inclUnapproved = pageUpToAndInclDati,
        inclTooRecent = this,
        version = pageVersion)

    val pageApproved = pageUpToAndInclDati.splitByApproval

    PageSplitByVersion(
      desired = pageApproved,
      inclUnapproved = pageUpToAndInclDati,
      inclTooRecent = this,
      version = pageVersion)
  }


  private def splitByTime(dati: ju.Date): Debate = {
    def happenedInTime(action: PostActionDtoOld) =
      action.ctime.getTime <= dati.getTime

    val (postsBefore, postsAfter) = posts partition happenedInTime
    val (ratingsBefore, ratingsAfter) = ratings partition happenedInTime
    val (editsBefore, editsAfter) = edits partition happenedInTime
    val (editAppsBefore, editAppsAfter) = editApps partition happenedInTime
    val (flagsBefore, flagsAfter) = flags partition happenedInTime
    val (deletionsBefore, deletionsAfter) = deletions partition happenedInTime
    val (reviewsBefore, reviewsAfter) = reviews partition happenedInTime
    val (actionsBefore, actionsAfter) = actionDtos partition happenedInTime

    val pageUpToAndInclDati = copy(
      posts = postsBefore,
      ratings = ratingsBefore,
      edits = editsBefore,
      editApps = editAppsBefore,
      flags = flagsBefore,
      deletions = deletionsBefore,
      reviews = reviewsBefore,
      actionDtos = actionsBefore)

    pageUpToAndInclDati
  }


  private def splitByApproval: Debate = {

      val (postsApproved, postsUnapproved) = posts partition { rawPost =>
        val post = vipo_!(rawPost.id)
        post.lastApprovalDati.isDefined
      }

      val (editsApproved, editsUnapproved) = edits partition { edit =>
        val post = vipo_!(edit.postId)
        post.lastApprovalDati match {
          case None => false
          case Some(approvalDati) => edit.ctime.getTime <= approvalDati.getTime
        }
      }

      val (editAppsApproved, editAppsUnapproved) = editApps partition { edApp =>
        val edit = editsById(edApp.editId)
        val post = vipo_!(edit.postId)
        post.lastApprovalDati match {
          case None => false
          case Some(approvalDati) => edApp.ctime.getTime <= approvalDati.getTime
        }
      }

      // As mentioned in the docs of splitByVersion, only Posts,
      // Edits and EditApps are partitioned.
      // BUG Should also partition deletions? In the future, when/if
      // they can be approved/rejected.
      val approvedVersionOfPage = copy(
        posts = postsApproved,
        edits = editsApproved,
        editApps = editAppsApproved)

    approvedVersionOfPage
  }


  // -------- Misc

  /**
   * The most recent outwardly visible action, e.g. the last edit application,
   * or the last reversion of an applied edit. Might also
   * return an even more recent action that does actually not affect
   * how this page is being rendered (e.g. a deletion of a pending edit).
   */
  //def lastOrLaterVisibleAction: Option[Action] = lastAction  // for now


  /**
   * The action with the most recent creation dati.
   */
  lazy val lastAction: Option[PostActionDtoOld] = oldestOrLatestAction(latest = true)


  /**
   * The action with the oldest creation dati.
   */
  private def oldestAction: Option[PostActionDtoOld] = oldestOrLatestAction(latest = false)


  private def oldestOrLatestAction(latest: Boolean): Option[PostActionDtoOld] = {
    def latestOrOldestAction(a: PostActionDtoOld, b: PostActionDtoOld) = {
      if (latest) {
        if (a.ctime.getTime < b.ctime.getTime) b else a
      }
      else {
        if (a.ctime.getTime < b.ctime.getTime) a else b
      }
    }

    // Edits might be auto applied, so check their dates.
    // Deletions might revert edit applications, so check their dates.
    // Hmm, check everything, or a bug will pop up, later on.
    val all = allActions
    if (all isEmpty) None
    else Some(all reduceLeft (latestOrOldestAction(_, _)))
  }


  /**
   * When the most recent change to this page was made —
   * but unappliied (i.e. pending) edits are ignored.
   * Might return a somewhat more recent date, e.g. for a pending edit
   * that was deleted (but not an earlier date, that'd be a bug).
   */
  //lazy val lastOrLaterChangeDate: Option[ju.Date] =
  //  lastOrLaterVisibleAction.map(_.ctime)


  lazy val modificationDati: Option[ju.Date] =
    lastAction.map(_.ctime)


  lazy val oldestDati: Option[ju.Date] =
    oldestAction.map(_.ctime)

}



case class PageVersion(dati: ju.Date, approved: Boolean) {

  def isLatest: Boolean = dati.getTime == Long.MaxValue
  def datiIsoStr: String = toIso8601T(dati)

}


object PageVersion {

  def latest(approved: Boolean) =
    PageVersion(new ju.Date(Long.MaxValue), approved)

  val LatestApproved = latest(approved = true)
  val LatestUnapproved = latest(approved = false)

}


case class PageSplitByVersion(
  /** Includes everything up to a certain date, and perhaps only approved things. */
  desired: Debate,
  /** Includes everything up to a certain date, also unapproved things. */
  inclUnapproved: Debate,
  /** Includes everything regardles of date and approval status. */
  inclTooRecent: Debate,
  /** The version at which the page was split. */
  version: PageVersion)



/**
 * Which post to use as the root post, e.g. when viewing a page, or when
 * sending updates of a page back to the browser (only posts below the
 * root post would be sent).
 */
sealed abstract class PageRoot {
  // COULD rename to `id`? Why did I call it `subId`?
  def subId: String
  // Why did I name it "...OrCreate..."?
  def findOrCreatePostIn(page: Debate): Option[Post]
  def findChildrenIn(page: Debate): List[Post]
  def isDefault: Boolean = subId == Page.BodyId
  def isPageConfigPost: Boolean = subId == Page.ConfigPostId
}


object PageRoot {

  val TheBody = Real(Page.BodyId)

  /** A real post, e.g. the page body post. */
  case class Real(subId: String) extends PageRoot {
    // Only virtual ids may contain hyphens, e.g. "page-template".
    assErrIf3(subId contains "-", "DwE0ksEW3", "Real id contains hyphen: "+
          safed(subId))

    def findOrCreatePostIn(page: Debate): Option[Post] = page.vipo(subId)

    def findChildrenIn(page: Debate): List[Post] =
      page.repliesTo(subId) map (new Post(page, _))
  }

  // In the future, something like this:
  // case class FlaggedPosts -- creates a virtual root post, with all
  // posts-with-flags as its children.
  // And lots of other virtual roots that provide whatever info on
  // the page?

  def apply(id: String): PageRoot = {
    id match {
      case null => assErr("DwE0392kr53", "Id is null")
      // COULD check if `id' is invalid, e.g.contains a hyphen,
      // and if so show an error page root post.
      case "" => Real(Page.BodyId)  // the default, if nothing specified
      case "title" => Real(Page.TitleId)
      case "template" => Real(Page.ConfigPostId)
      case id => Real(id)
    }
  }
}


