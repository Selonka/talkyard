/// <reference path="../test-types.ts"/>

import * as _ from 'lodash';
import assert = require('assert');
import server = require('../utils/server');
import utils = require('../utils/utils');
import pagesFor = require('../utils/pages-for');
import settings = require('../utils/settings');
import make = require('../utils/make');
import logAndDie = require('../utils/log-and-die');
import c = require('../test-constants');

declare var browser: any;
declare var browserA: any;
declare var browserB: any;

let richBrowserA;
let richBrowserB;
let owen: Member;
let owensBrowser;
let maria: Member;
let mariasBrowser;
let michael: Member;
let michaelsBrowser;
let stranger;
let strangersBrowser;

let idAddress;
let forumTitle = "Delete Messages Forum";
let formalMessagesUrl;
let privChatUrl;

let siteId;

let mariasMessageTitle = 'mariasMessageTitle';
let mariasMessageText = 'mariasMessageText';
let mariasMessagePageId: string;

describe("direct-messages-delete.2browsers.test.ts  TyT5033FKSNS57", () => {

  it("initialize people", () => {
    richBrowserA = _.assign(browserA, pagesFor(browserA));
    richBrowserB = _.assign(browserB, pagesFor(browserB));

    owen =  make.memberOwenOwner();
    michael = make.memberMichael();
    maria = make.memberMaria();

    owensBrowser = richBrowserA;
    mariasBrowser = richBrowserB;
    michaelsBrowser = richBrowserB;
    strangersBrowser = richBrowserB;
  });

  it("import a site", () => {
    let site: SiteData = make.forumOwnedByOwen('formal-priv-msg', { title: forumTitle });
    site.settings.allowGuestLogin = true;
    site.settings.requireVerifiedEmail = false;

    const owen = site.members[0];
    assert.equal(owen.username, 'owen_owner');
    owen.emailNotfPrefs = EmailNotfPrefs.ReceiveAlways;  // (6029WKHU4)

    site.members.push(michael);
    site.members.push(maria);
    idAddress = server.importSiteData(site);
    siteId = idAddress.id;
  });


  // Generate private message discussion
  // ------------------------------------------------------

  it("Maria goes to Owen's profile page", () => {
    mariasBrowser.userProfilePage.openActivityFor(owen.username, idAddress.origin);
  });

  it("... logs in via topbar", () => {
    mariasBrowser.complex.loginWithPasswordViaTopbar(maria);
  });

  let messagePageId;

  it("... sends a formal private message", () => {
    mariasBrowser.userProfilePage.clickSendMessage();
    mariasBrowser.editor.editTitle(mariasMessageTitle);
    mariasBrowser.editor.editText(mariasMessageText);
    mariasBrowser.editor.saveWaitForNewPage();
    mariasBrowser.assertPageTitleMatches(mariasMessageTitle);
    mariasBrowser.assertPageBodyMatches(mariasMessageText);
    //messageUrl = mariasBrowser.getUrl();
    mariasMessagePageId = mariasBrowser.getPageId();
  });

  /*
  let emailToOwen: EmailSubjectBody;

  it("Owen gets a notf email", () => {
    emailToOwen = server.waitUntilLastEmailMatches(
        siteId, owen.emailAddress, [messageTitle, messageText], browser).matchedEmail;
  });

  it("... clicks the notf link", () => {
    const replyNotfLink = utils.findFirstLinkToUrlIn(
        // Currently the link uses the page id, not url slug.
        // So, not:  + firstUpsertedPage.urlPaths.canonical
        // Instead,  /-1:
        'https?://.*    /-' + messagePageId, emailToOwen.bodyHtmlText);
  });

  it("Owen logs in", () => {
    // ???
    owen.go(idAddress.origin);
    owen.complex.loginWithPasswordViaTopbar(owen);
  });

  it("... he doesn't see the message in the topic list", () => {
    owen.forumTopicList.waitUntilKnowsIsEmpty();
  });

  it("... but sees a notification", () => {
    owen.topbar.assertNotfToMe();
  });

  it("... opens the message via the notf icon", () => {
    owen.topbar.openNotfToMe();
  });

  it("... and replies", () => {
    owen.complex.replyToOrigPost(owensAnswer);
    owen.topic.waitForPostNrVisible(c.FirstReplyNr);
  });

  it("... he also got an email notf about this new topic", () => {
    // This tests notfs when one clicks the append-bottom-comment button.
    server.waitUntilLastEmailMatches(
        siteId, owen.emailAddress, [messageTitle, messageText], browser);
  });

  it("Maria sees the reply", () => {
    // This fails (times out) if Nchan messed up internally, because of an Nginx worker thread crash.
    maria.topic.waitForPostAssertTextMatches(c.FirstReplyNr, owensAnswer);
  });

  it("... and replies", () => {
    maria.complex.replyToPostNr(2, mariasQuestion);
    maria.topic.waitForPostAssertTextMatches(3, mariasQuestion);
  });

  it("... she got a notification, dismisses it", () => {
    maria.topbar.openNotfToMe();
  });
  */

});

