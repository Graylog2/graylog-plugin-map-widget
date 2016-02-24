import React, {PropTypes} from 'react';
import Reflux from 'reflux';
import { Map, TileLayer, CircleMarker, Popup } from 'react-leaflet';
import CustomGeoJson from 'components/CustomGeoJson';
import { Spinner } from 'components/common';

import 'leaflet/dist/leaflet.css';

import { MapsActions, MapsStore } from 'stores/MapsStore';

const MapVisualization = React.createClass({
  propTypes: {
    id: PropTypes.string.isRequired,
    data: PropTypes.object,
    config: PropTypes.object.isRequired,
    height: PropTypes.number,
    width: PropTypes.number,
    url: PropTypes.string,
    attribution: PropTypes.string,
  },
  mixins: [Reflux.connect(MapsStore)],
  getDefaultProps() {
    return {
      data: {},
    };
  },
  getInitialState() {
    return {
      zoomLevel: 1,
      isComponentMounted: false, // Leaflet operates directly with the DOM, so we need to wait until it is ready :grumpy:
    };
  },
  componentDidMount() {
    MapsActions.getGeoJson();
    this._onComponentMount();
  },
  _onComponentMount() {
    this.setState({isComponentMounted: true});
  },
  position: [0, 0],
  DEFAULT_URL: `https://api.mapbox.com/v4/mapbox.streets/{z}/{x}/{y}.png?access_token=${window.mapConfig.mapboxAccessToken}`,
  DEFAULT_ATTRIBUTION: `<a href="http://www.mapbox.com/about/maps/" target="_blank">Terms &amp; Feedback</a>`,
  // Coordinates are given as "lat,long"
  _formatMarker(coordinates, occurrences, min, max, increment) {
    const formattedCoordinates = coordinates.split(',').map(component => Number(component));
    const radius = this._getBucket(occurrences, 10, min, max, increment);
    return (
      <CircleMarker key={coordinates} center={formattedCoordinates} radius={radius} color="#AF2228" fillColor="#D3242B" weight={2} opacity={0.8}>
        <Popup>
            <dl>
              <dt>Coordinates:</dt>
              <dd>{coordinates}</dd>
              <dt>Number of occurrences:</dt>
              <dd>{occurrences}</dd>
            </dl>
        </Popup>
      </CircleMarker>
    );
  },
  _onZoomChange(event) {
    this.setState({zoomLevel: event.target.getZoom()});
  },
  _getBucket(value, bucketCount, minValue, maxValue, increment) {
    // Calculate bucket size based on min/max value and the number of buckets.
    const bucketSize = (maxValue - minValue) / bucketCount;
    // Select bucket for the given value.
    const bucket = value < maxValue ? Math.ceil((value - minValue) / bucketSize) : bucketCount;

    return bucket + increment;
  },
  _handleGeoLayer(layer) {
    layer.setStyle({
      fillColor: '#003171',
      fillOpacity: 1,
      color: '#555',
      weight: 1,
      opacity: 0.5,
    });
  },
  render() {
    if (!this.state.isComponentMounted) {
      return <Spinner/>;
    }

    const data = this.props.data;
    const occurrences = Object.keys(data).map((k) => data[k]);
    const minOccurrences = occurrences.reduce((prev, cur) => Math.min(prev, cur), Infinity);
    const maxOccurrences = occurrences.reduce((prev, cur) => Math.max(prev, cur), -Infinity);
    const increment = this._getBucket(this.state.zoomLevel, 10, 1, 10, 1);

    const coordinates = Object.keys(data);
    const markers = coordinates.map(aCoordinates => this._formatMarker(aCoordinates, data[aCoordinates], minOccurrences, maxOccurrences, increment));
    const leafletUrl = this.props.url || this.DEFAULT_URL;
    const leafletAttribution = this.props.attribution || this.DEFAULT_ATTRIBUTION;

    let geoJson;
    if (this.state.geoJson) {
      geoJson = (<CustomGeoJson ref="geojson" handleLayer={this._handleGeoLayer} data={this.state.geoJson} />);
    } else {
      geoJson = null;
    }

    /*
    if (this.refs.geojson) {
      this.refs.geojson.getLeafletElement().eachLayer((layer) => {
        layer.setStyle({
          fillColor: '#003171',
          fillOpacity: 1,
          color: '#555',
          weight: 1,
          opacity: 0.5,
        });
      });
    }
    */

    let tileLayer = (<TileLayer url={leafletUrl} maxZoom={19} attribution={leafletAttribution}/>);

    return (
      <Map center={this.position} zoom={this.state.zoomLevel} onZoomend={this._onZoomChange} style={{height: this.props.height, width: this.props.width}} scrollWheelZoom={false}>
        {geoJson}
        {markers}
      </Map>
    );
  },
});

export default MapVisualization;
