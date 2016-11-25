/**
 * Created by cmeyers on 8/26/16.
 */
import React, { Component, PropTypes } from 'react';
import { Icon } from 'react-material-icons-blue';
import {
    RunApi as runApi,
    ToastService as toastService,
    ToastUtils,
} from '../';
import Security from '../security';
import i18nTransFactory from '../i18n/i18n';

const translate = i18nTransFactory('blueocean-web', 'jenkins.plugins.blueocean.web.Messages');

const { permit } = Security;

const stopProp = (event) => {
    event.stopPropagation();
};

/**
 * Run Buttons allows a pipeline or branch to be run and also be stopped thereafter.
 */
export class RunButton extends Component {

    constructor(props) {
        super(props);

        this.state = {
            running: false,
            stopping: false,
        };
    }

    componentWillReceiveProps(nextProps) {
        this._updateState(nextProps);
    }

    _updateState(nextProps) {
        const oldStatus = this.props.latestRun && this.props.latestRun.state || '';
        const newStatus = nextProps.latestRun && nextProps.latestRun.state || '';

        // if the state of the run changed, then assume it's no longer trying to stop
        if (oldStatus !== newStatus) {
            this.setState({
                stopping: false,
            });
        }
    }

    _onRunClick() {
        runApi.startRun(this.props.runnable)
            .then((runInfo) => ToastUtils.createRunStartedToast(this.props.runnable, runInfo, this.props.onNavigation));
    }

    _onStopClick() {
        if (this.state.stopping) {
            return;
        }

        this.setState({
            stopping: true,
        });

        if (this.props.latestRun.state === 'QUEUED') {
            runApi.removeFromQueue(this.props.latestRun);
        } else {
            runApi.stopRun(this.props.latestRun);
        }

        const name = decodeURIComponent(this.props.runnable.name);
        const runId = this.props.latestRun.id;
        const text = translate('toast.run.stopping', {
            0: name,
            1: runId,
            defaultValue: 'Stoppping "{0}" #{1}',
        });

        toastService.newToast({ text });
    }

    render() {
        const outerClass = this.props.className ? this.props.className : '';
        const outerClassNames = outerClass.split(' ');
        const innerButtonClass = outerClassNames.indexOf('icon-button') === -1 ? 'btn inverse' : '';
        const stopClass = this.state.stopping ? 'stopping' : '';

        const status = this.props.latestRun ? this.props.latestRun.state : '';
        const runningStatus = status && (status.toLowerCase() === 'running' || status.toLowerCase() === 'queued');

        let showRunButton = this.props.buttonType === 'run-only' || (this.props.buttonType === 'toggle' && !runningStatus);
        let showStopButton = runningStatus && (this.props.buttonType === 'toggle' || this.props.buttonType === 'stop-only');

        showRunButton = showRunButton && permit(this.props.runnable).start();
        showStopButton = showStopButton && permit(this.props.runnable).stop();

        const runLabel = this.props.runText || translate('toast.run', {
            defaultValue: 'Run',
        });
        const stopLabel = this.state.stopping ? translate('toast.stopping', {
            defaultValue: 'Stopping ...',
        }) : translate('toast.stop', {
            defaultValue: 'Stop',
        });

        if (!showRunButton && !showStopButton) {
            return null;
        }

        return (
            <div className={`run-button-component ${outerClass}`} onClick={(event => stopProp(event))}>
                { showRunButton &&
                <a className={`run-button ${innerButtonClass}`} title={runLabel} onClick={() => this._onRunClick()}>
                    <Icon size={24} icon="play_circle_outline" />
                    <span className="button-label">{runLabel}</span>
                </a>
                }

                { showStopButton &&
                <a className={`stop-button ${innerButtonClass} ${stopClass}`} title={stopLabel} onClick={() => this._onStopClick()}>
                    { /* eslint-disable max-len */ }
                    <svg className="svg-icon" width="20" height="20" viewBox="0 0 20 20" xmlns="http://www.w3.org/2000/svg">
                        <g fill="none" fill-rule="evenodd">
                            <path d="M-2-2h24v24H-2z" />
                            <path className="svg-icon-inner" d="M10 0C4.48 0 0 4.48 0 10s4.48 10 10 10 10-4.48 10-10S15.52 0 10 0zM7 7h6v6H7V7zm3 11c-4.41 0-8-3.59-8-8s3.59-8 8-8 8 3.59 8 8-3.59 8-8 8z" fill="#4A90E2" />
                        </g>
                    </svg>
                    { /* eslint-enable max-len */ }
                    <span className="button-label">{stopLabel}</span>
                </a>
                }
            </div>
        );
    }
}

RunButton.propTypes = {
    buttonType: PropTypes.oneOf('toggle', 'stop-only', 'run-only'),
    className: PropTypes.string,
    runnable: PropTypes.object,
    latestRun: PropTypes.object,
    onNavigation: PropTypes.func,
    runText: PropTypes.string,
};

RunButton.defaultProps = {
    buttonType: 'toggle',
};
