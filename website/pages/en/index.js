/**
 * Copyright (c) 2017-present, Facebook, Inc.
 *
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 */

const React = require("react");

const CompLibrary = require("../../core/CompLibrary.js");

const MarkdownBlock = CompLibrary.MarkdownBlock; /* Used to read markdown */
const Container = CompLibrary.Container;
const GridBlock = CompLibrary.GridBlock;

class HomeSplash extends React.Component {
  render() {
    const { siteConfig, language = "" } = this.props;
    const { baseUrl, docsUrl } = siteConfig;
    const docsPart = `${docsUrl ? `${docsUrl}/` : ""}`;
    const langPart = `${language ? `${language}/` : ""}`;
    const docUrl = doc => `${baseUrl}${docsPart}${langPart}${doc}`;

    const SplashContainer = props => (
      <div className="homeContainer">
        <div className="homeSplashFade">
          <div className="wrapper homeWrapper">{props.children}</div>
        </div>
      </div>
    );

    const Logo = props => (
      <div className="projectLogo">
        <img src={props.img_src} alt="Project Logo" />
      </div>
    );

    const ProjectTitle = () => (
      <div>
        <h2 className="projectTitle">
          <small>{siteConfig.tagline}</small>
        </h2>
      </div>
    );

    const PromoSection = props => (
      <div className="section promoSection">
        <div className="promoRow">
          <div className="pluginRowBlock">{props.children}</div>
        </div>
      </div>
    );

    const Button = props => (
      <div className="pluginWrapper buttonWrapper">
        <a className="button" href={props.href} target={props.target}>
          {props.children}
        </a>
      </div>
    );

    return (
      <SplashContainer>
        <div className="inner">
          <img
            src={`${baseUrl}img/granary-logo.png`}
            alt="Azavea"
            width="30%"
          />
          <ProjectTitle siteConfig={siteConfig} />
        </div>
      </SplashContainer>
    );
  }
}

class Index extends React.Component {
  render() {
    const { config: siteConfig, language = "" } = this.props;
    const { baseUrl } = siteConfig;

    const Block = props => (
      <Container
        padding={["bottom", "top"]}
        id={props.id}
        background={props.background}
      >
        <GridBlock
          align="left"
          contents={props.children}
          layout={props.layout}
        />
      </Container>
    );

    const FeatureCallout = () => (
      <div
        className="productShowcaseSection paddingBottom"
        style={{ textAlign: "center" }}
      >
        <h2></h2>
      </div>
    );

    const TryOut = () => (
      <Block id="try">
        {[
          {
            content:
              "Granary associates prediction runs with your models and keeps track " +
              "of where their results are stored. With signed urls, Granary makes " +
              "serving predictions stored on S3 to client applications easy.",
            image: `${baseUrl}img/undraw_predictive_analytics_kf9n.svg`,
            imageAlign: "left",
            title: "Keep track of your predictions and their results"
          }
        ]}
      </Block>
    );

    const Description = () => (
      <Block background="dark">
        {[
          {
            content:
              "Granary puts some guardrails in front of your interaction with Batch " +
              "so you don't fall in to common mistakes, like mis-formatted arguments," +
              "missing arguments, and bad string values for important AWS resources.",
            image: `${baseUrl}img/undraw_done_checking_ty9a.svg`,
            imageAlign: "right",
            title:
              "Bring some sanity to your day-to-day modeling work in AWS Batch"
          }
        ]}
      </Block>
    );

    const LearnHow = () => (
      <Block background="light">
        {[
          {
            content:
              "Create a model with AWS resources configured once, then " +
              "never worry about remembering the job definition or job queue names " +
              "again for the rest of your life.",
            image: `${baseUrl}img/undraw_feeling_proud_qne1.svg`,
            imageAlign: "right",
            title:
              "Run models in AWS Batch without worrying about infrastructure more than once"
          }
        ]}
      </Block>
    );

    const Showcase = () => {
      if ((siteConfig.users || []).length === 0) {
        return null;
      }

      const showcase = siteConfig.users
        .filter(user => user.pinned)
        .map(user => (
          <a href={user.infoLink} key={user.infoLink}>
            <img src={user.image} alt={user.caption} title={user.caption} />
          </a>
        ));

      const pageUrl = page => baseUrl + (language ? `${language}/` : "") + page;

      return (
        <div className="productShowcaseSection paddingBottom">
          <h2>Who is Using This?</h2>
          <p>This project is used by all these people</p>
          <div className="logos">{showcase}</div>
          <div className="more-users">
            <a className="button" href={pageUrl("users.html")}>
              More {siteConfig.title} Users
            </a>
          </div>
        </div>
      );
    };

    return (
      <div>
        <HomeSplash siteConfig={siteConfig} language={language} />
        <div className="mainContainer">
          <FeatureCallout />
          <LearnHow />
          <TryOut />
          <Description />
        </div>
      </div>
    );
  }
}

module.exports = Index;
