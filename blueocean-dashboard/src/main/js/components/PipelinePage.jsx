import React, { Component, PropTypes } from 'react';
import { Link } from 'react-router';
import Extensions from '@jenkins-cd/js-extensions';
import {
    ExpandablePath,
    Page,
    PageHeader,
    Title,
    PageTabs,
    TabLink,
    WeatherIcon,
} from '@jenkins-cd/design-language';
import { i18nTransFactory, NotFound, User, Paths } from '@jenkins-cd/blueocean-core-js';
import { Icon } from 'react-material-icons-blue';
import PageLoading from './PageLoading';
import { buildOrganizationUrl, buildPipelineUrl, buildClassicConfigUrl } from '../util/UrlUtils';
import { documentTitle } from './DocumentTitle';
import { observer } from 'mobx-react';
import { observable, action } from 'mobx';

const RestPaths = Paths.rest;
/**
 * returns true if the pipeline is defined and has branchNames
 */
export function pipelineBranchesUnsupported(pipeline) {
    return (pipeline && !pipeline.branchNames) ||
      (pipeline && !pipeline.branchNames.length);
}

const classicConfigLink = (pipeline) => {
    let link = null;
    if (!User.current().isAnonymous()) {
        link = <a href={buildClassicConfigUrl(pipeline)} target="_blank"><Icon size={24} icon="settings" style={{ fill: '#fff' }} /></a>;
    }
    return link;
};

const translate = i18nTransFactory('blueocean-dashboard', 'jenkins.plugins.blueocean.dashboard.Messages');

@observer
export class PipelinePage extends Component {


    componentWillMount() {
        if (this.props.params) {
            this.href = RestPaths.pipeline(this.props.params.organization, this.props.params.pipeline);
            this.context.pipelineService.fetchPipeline(this.href, { useCache: true }).catch(err => this._setError(err));
        }
    }

    @observable error;

    @action
    _setError(error) {
        this.error = error;
    }


    render() {
        const pipeline = this.context.pipelineService.getPipeline(this.href);

        const { setTitle } = this.props;
        const { location = {} } = this.context;

        const { organization, name, fullName, fullDisplayName } = pipeline || {};
        const orgUrl = buildOrganizationUrl(organization);
        const activityUrl = buildPipelineUrl(organization, fullName, 'activity');
        const isReady = !!pipeline;

        if (!pipeline && this.error) {
            return <NotFound />;
        }

        setTitle(`${organization} / ${name}`);

        const baseUrl = buildPipelineUrl(organization, fullName);
        return (
            <Page>
                <PageHeader>
                    {!isReady && <PageLoading duration={2000} />}
                    {!isReady &&
                    <Title>
                        <h1><Link to={orgUrl}>{organization}</Link>
                        <span> / </span></h1>
                    </Title>}
                    {isReady &&
                    <Title>
                        <WeatherIcon score={pipeline.weatherScore} size="large" />
                        <h1>
                            <Link to={orgUrl} query={location.query}>{organization}</Link>
                            <span>&nbsp;/&nbsp;</span>
                            <Link to={activityUrl} query={location.query}>
                                <ExpandablePath path={fullDisplayName} hideFirst className="dark-theme" iconSize={20} />
                            </Link>
                        </h1>
                        <Extensions.Renderer
                          extensionPoint="jenkins.pipeline.detail.header.action"
                          store={this.context.store}
                          pipeline={pipeline}
                        />
                        {classicConfigLink(pipeline)}
                    </Title>
                    }

                    <PageTabs base={baseUrl}>
                        <TabLink to="/activity">{ translate('pipelinedetail.common.tab.activity', { defaultValue: 'Activity' }) }</TabLink>
                        <TabLink to="/branches">{ translate('pipelinedetail.common.tab.branches', { defaultValue: 'Branches' }) }</TabLink>
                        <TabLink to="/pr">{ translate('pipelinedetail.common.tab.pullrequests', { defaultValue: 'Pull Requests' }) }</TabLink>
                    </PageTabs>
                </PageHeader>
                {isReady && React.cloneElement(this.props.children, { pipeline, setTitle, t: translate, locale: translate.lng })}
            </Page>
        );
    }
}

PipelinePage.propTypes = {
    children: PropTypes.any,
    pipeline: PropTypes.any,
    params: PropTypes.object,
    setTitle: PropTypes.func,
};


PipelinePage.contextTypes = {
    config: PropTypes.object.isRequired,
    location: PropTypes.object,
    store: PropTypes.object,
    pipelineService: PropTypes.object,
};

export default documentTitle(PipelinePage);

