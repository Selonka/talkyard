import WDIOReporter from '@wdio/reporter'
import * as ansiColors from 'ansi-colors';


function logProgr(message: string) {
  console.log(ansiColors.whiteBright(message));
}

function logProgrBold(message: string) {
  console.log(ansiColors.bold.whiteBright(message));
}


function nowString(): string {
  return (new Date()).toISOString();
}

let thisFileStartMs;
let suiteStartMs;
let numSpecs = 0;
let numFailures = 0;


// Prints the current test name, so if a test hangs, one sees which test
// (because if you terminate the hanged test via CTRL+C, you'll kill the whole
// process and it'll never get the chance to tell you which test hanged).
//
export = class TyWdioReporter extends WDIOReporter {
    //reporterName = 'ProgressReporter';

    onRunnerStart() { // everythingInfo: EverythingInfo) {
      thisFileStartMs = Date.now();
    }

    onRunnerEnd() {
      const endMs = Date.now();
      const durSecs = (endMs - thisFileStartMs) / 1000;
      const durMins = Math.floor(durSecs / 60);
      const remRoundSecs = Math.round(durSecs - durMins * 60);
      logProgrBold(`Done running ${numSpecs} specs, ` +
          `took ${durMins}m ${remRoundSecs}s, ${numFailures} failures. [TyM5WKAB02]`);
    }

    onSuiteStart(suite: WDIOReporter.Suite) {
      /*
      // Don't log this, for nested suites (a  describe(){...} inside a test file).
      // if (suite.parentUid !== suite.uid) return;  — is the same, also for nested suites.
      // suite.title and .parent and .fullTitle are different though:
      if (suite.title !== suite.parent) return;
      */
      numSpecs += 1;
      /*
      console.log('title: ' + suite.title + ' parent: ' + suite.parent + ' fullTtl: ' + suite.fullTitle);
      console.log('parentUid: ' + suite.parentUid +  '   uid: ' + suite.uid); *  /
      */
      suiteStartMs = Date.now();
      //console.log(`${suite.cid}: ■■■ Spec start: ${suite.fullTitle}, ${nowString()}`);
      logProgrBold(`Suite start: "${suite.fullTitle}", ${nowString()}`);
    }

    onSuiteEnd(suite: WDIOReporter.Suite) {
      //if (suite.title !== suite.parent) return;
      const endMs = Date.now();
      const durSecs = Math.round((endMs - suiteStartMs) / 1000);
      logProgrBold(`Suite ended after ${durSecs} seconds: "${suite.title}", ${nowString()}`);
    }

    onTestStart(test: WDIOReporter.Test) {
      logProgr(`${test.title}`);
    }

    onTestEnd(test: WDIOReporter.Test) {
    }

    onHookStart(test: WDIOReporter.Hook) {
    }

    onHookEnd(test: WDIOReporter.Hook) {
    }

    onTestPass(test: WDIOReporter.Test) {
    }

    onTestFail(test: WDIOReporter.Test) {
      numFailures += 1;
      const endMs = Date.now();
      const durSecs = Math.round((endMs - suiteStartMs) / 1000);
      logProgrBold(``);
      logProgrBold(`FAILED after ${durSecs}s: "${test.title}", ${nowString()} [TyEE2EFAIL]`);
      //console.log(`Stack traces:\n${test.error.stack}`);
      logProgr(`Stack trace:\n${test.error.stack}`);
      logProgr(``);
    }

    onTestSkip(test: WDIOReporter.Test) {
      logProgr(`SKIPPING: ${test.title}`);
    }
};