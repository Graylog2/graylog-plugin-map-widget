import { PropTypes } from 'react';
import { geoJson } from 'leaflet';
import { Path } from 'react-leaflet';

export default class CustomGeoJson extends Path {
  componentWillMount() {
    super.componentWillMount();
    const { data, map, ...props } = this.props;
    this.leafletElement = geoJson(data, props);

    if (typeof this.props.handleLayer === 'function') {
      this.leafletElement.eachLayer(this.props.handleLayer);
    }
  }

  componentDidUpdate(prevProps) {
    this.setStyleIfChanged(prevProps, this.props);
  }
}

CustomGeoJson.propTypes = {
  data: PropTypes.object.isRequired,
  handleLayer: PropTypes.func,
};