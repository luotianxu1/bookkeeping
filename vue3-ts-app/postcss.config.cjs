module.exports = {
  plugins: {
    'postcss-pxtorem': {
      rootValue: 37.5,
      propList: ['*'],
      selectorBlackList: ['.norem'],
      minPixelValue: 2,
      mediaQuery: false,
      exclude: /node_modules/i,
    },
  },
}
