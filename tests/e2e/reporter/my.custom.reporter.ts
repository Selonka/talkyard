/* eslint-disable no-console */

import WDIOReporter from '@wdio/reporter' // .default

module.exports = class CustomReporter extends WDIOReporter {
    constructor (options) {
        super(options)
        console.log('initialised custom reporter with the following reporter options:', options)

        this.write('Some log line')
    }

    onRunnerStart () { console.log('onRunnerStart')}
    onBeforeCommand () { console.log('onBeforeCommand') }
    onAfterCommand () { console.log('onAfterCommand') }
    onScreenshot () { console.log('onScreenshot') }
    onSuiteStart () { console.log('onSuiteStart') }
    onHookStart () { console.log('onHookStart') }
    onHookEnd () { console.log('onHookEnd') }
    onTestStart () { console.log('onTestStart') }
    onTestPass () { console.log('onTestPass') }
    onTestFail () { console.log('onTestFail') }
    onTestSkip () { console.log('onTestSkip') }
    onTestEnd () { console.log('onTestEnd') }
    onSuiteEnd () { console.log('onSuiteEnd') }
    onRunnerEnd () { console.log('onRunnerEnd') }
}