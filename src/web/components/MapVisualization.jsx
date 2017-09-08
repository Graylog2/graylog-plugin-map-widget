/* eslint-env browser */
import PropTypes from 'prop-types';
import React from 'react';
import { Map, TileLayer, CircleMarker, Popup } from 'react-leaflet';

import {} from 'leaflet/dist/leaflet.css';
import style from './MapVisualization.css';

const MapVisualization = React.createClass({
  propTypes: {
    id: PropTypes.string.isRequired,
    data: PropTypes.object,
    height: PropTypes.number.isRequired,
    width: PropTypes.number.isRequired,
    url: PropTypes.string,
    attribution: PropTypes.string,
    interactive: PropTypes.bool,
    onRenderComplete: PropTypes.func,
  },
  getDefaultProps() {
    return {
      data: {},
      url: 'https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png',
      attribution: '&copy; <a href="http://osm.org/copyright" target="_blank">OpenStreetMap</a> contributors',
      interactive: true,
      onRenderComplete: () => {},
    };
  },

  getInitialState() {
    return {
      zoomLevel: 1,
    };
  },

  componentDidMount() {
    this._forceMapUpdate();
  },

  componentDidUpdate(prevProps) {
    if (this.props.height !== prevProps.height || this.props.width !== prevProps.width) {
      this._forceMapUpdate();
    }
  },

  _map: undefined,
  position: [0, 0],
  MARKER_RADIUS_SIZES: 10,
  MARKER_RADIUS_INCREMENT_SIZES: 10,

  // Workaround to avoid wrong placed markers or empty tiles if the map container size changed.
  _forceMapUpdate() {
    if (this._map) {
      this._map.leafletElement.invalidateSize();
      window.dispatchEvent(new Event('resize'));
    }
  },

  // Coordinates are given as "lat,long"
  _formatMarker(coordinates, occurrences, min, max, increment) {
    const formattedCoordinates = coordinates.split(',').map(component => Number(component));
    const radius = this._getBucket(occurrences, this.MARKER_RADIUS_SIZES, min, max, increment);
    return (
      <CircleMarker key={coordinates} center={formattedCoordinates} radius={radius} color="#AF2228" fillColor="#D3242B"
                    weight={2} opacity={0.8}>
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
    this.setState({ zoomLevel: event.target.getZoom() });
  },
  _getBucket(value, bucketCount, minValue, maxValue, increment) {
    // Calculate bucket size based on min/max value and the number of buckets.
    const bucketSize = (maxValue - minValue) / bucketCount;
    // Select bucket for the given value.
    const bucket = value < maxValue ? Math.ceil((value - minValue) / bucketSize) : bucketCount;

    return bucket + increment;
  },

  render() {
    const { data, id, height, width, url, attribution, interactive, onRenderComplete } = this.props;

    const terms = data.terms;
    const occurrences = Object.keys(terms).map(k => terms[k]);
    const minOccurrences = occurrences.reduce((prev, cur) => Math.min(prev, cur), Infinity);
    const maxOccurrences = occurrences.reduce((prev, cur) => Math.max(prev, cur), -Infinity);
    const increment = this._getBucket(this.state.zoomLevel, this.MARKER_RADIUS_INCREMENT_SIZES, 1, 10, 1);

    const coordinates = Object.keys(terms);
    const markers = coordinates.map(aCoordinates => this._formatMarker(aCoordinates, terms[aCoordinates], minOccurrences, maxOccurrences, increment));

    return (
      <Map ref={(c) => { this._map = c; }}
           id={`visualization-${id}`}
           center={this.position}
           zoom={this.state.zoomLevel}
           onZoomend={this._onZoomChange}
           className={style.map}
           style={{ height: height, width: width }}
           scrollWheelZoom={false}
           animate={interactive}
           whenReady={onRenderComplete}>
        <TileLayer url={url} maxZoom={19} attribution={attribution} />
        {markers}
      </Map>
    );
  },
});

export default MapVisualization;
