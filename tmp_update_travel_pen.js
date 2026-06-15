const fs = require('fs');

const path = 'app.pen';
const data = JSON.parse(fs.readFileSync(path, 'utf8'));

const REMOVE_NAMES = new Set([
  '旅行管理',
  '旅行管理-新增旅行',
  '旅行管理-概览',
  '旅行管理-线路规划',
  '旅行管理-费用管理',
]);

data.children = data.children.filter(
  (node) => !(node.type === 'frame' && REMOVE_NAMES.has(node.name)),
);

const baseX = 25889;
const gapX = 473;
const screenPadding = [10, 16, 16, 16];

function text(id, name, content, fill, fontSize, fontWeight, extra = {}) {
  return {
    type: 'text',
    id,
    name,
    content,
    fill,
    fontFamily: extra.fontFamily || 'Inter',
    fontSize,
    fontWeight,
    ...extra,
  };
}

function mono(id, name, content, fill, fontSize, fontWeight, extra = {}) {
  return text(id, name, content, fill, fontSize, fontWeight, {
    ...extra,
    fontFamily: 'IBM Plex Mono',
  });
}

function icon(id, name, iconName, fill) {
  return {
    type: 'icon',
    id,
    name,
    library: 'Material Symbols Rounded',
    icon: iconName,
    width: 20,
    height: 20,
    fill,
  };
}

function pill(id, name, content, bg, textFill, stroke) {
  return {
    type: 'frame',
    id,
    name,
    width: 72,
    height: 28,
    fill: bg,
    cornerRadius: 999,
    stroke,
    strokeWidth: 1,
    strokeAlignment: 'inner',
    layout: 'vertical',
    justifyContent: 'center',
    alignItems: 'center',
    children: [text(`${id}_text`, 'label', content, textFill, 11, '700')],
  };
}

function chip(id, name, content, fill, textFill, stroke) {
  return {
    type: 'frame',
    id,
    name,
    height: 32,
    fill,
    cornerRadius: 999,
    ...(stroke
      ? { stroke, strokeWidth: 1, strokeAlignment: 'inner' }
      : {}),
    layout: 'horizontal',
    alignItems: 'center',
    padding: [0, 12],
    children: [text(`${id}_text`, 'chipLabel', content, textFill, 12, '600')],
  };
}

function actionButton(id, content, primary = false) {
  return {
    type: 'frame',
    id,
    name: content,
    width: 'fill_container',
    height: primary ? 48 : 44,
    fill: primary ? '#1D4ED8' : '#FFFFFF',
    cornerRadius: primary ? 16 : 14,
    ...(primary
      ? {}
      : { stroke: '#E2E8F0', strokeWidth: 1, strokeAlignment: 'inner' }),
    layout: 'vertical',
    justifyContent: 'center',
    alignItems: 'center',
    children: [
      text(
        `${id}_text`,
        'btnLabel',
        content,
        primary ? '#FFFFFF' : '#334155',
        primary ? 15 : 14,
        primary ? '700' : '600',
      ),
    ],
  };
}

function progressTrack(id, fillWidth, fillColor) {
  return {
    type: 'frame',
    id,
    name: 'track',
    width: 'fill_container',
    height: 8,
    fill: '#E2E8F0',
    cornerRadius: 999,
    layout: 'none',
    children: [
      {
        type: 'frame',
        id: `${id}_fill`,
        name: 'fill',
        x: 0,
        y: 0,
        width: fillWidth,
        height: 8,
        fill: fillColor,
        cornerRadius: 999,
      },
    ],
  };
}

function field(id, label, value, opts = {}) {
  return {
    type: 'frame',
    id,
    name: label,
    width: 'fill_container',
    layout: 'vertical',
    gap: 6,
    children: [
      text(`${id}_label`, 'label', label, '#334155', 13, '600'),
      {
        type: 'frame',
        id: `${id}_box`,
        name: 'field',
        width: 'fill_container',
        height: opts.height || 52,
        fill: '#FFFFFF',
        cornerRadius: 16,
        stroke: '#E2E8F0',
        strokeWidth: 1,
        strokeAlignment: 'inner',
        layout: opts.multiline ? 'vertical' : 'horizontal',
        alignItems: opts.multiline ? undefined : 'center',
        padding: opts.multiline ? 14 : [0, 14],
        children: [
          (opts.mono ? mono : text)(
            `${id}_value`,
            'value',
            value,
            opts.placeholder ? '#64748B' : '#0F172A',
            opts.multiline ? 14 : 15,
            opts.multiline ? '500' : opts.mono ? '700' : '600',
            opts.multiline
              ? { textGrowth: 'fixed-width', width: 'fill_container' }
              : {},
          ),
        ],
      },
    ],
  };
}

const shadow = {
  type: 'shadow',
  shadowType: 'outer',
  color: '#0F172A0D',
  offset: { x: 0, y: 10 },
  blur: 24,
};

const heroGradient = {
  type: 'gradient',
  gradientType: 'linear',
  enabled: true,
  rotation: 135,
  size: { height: 1 },
  colors: [
    { color: '#EFF6FF', position: 0 },
    { color: '#DBEAFE', position: 1 },
  ],
};

const fabGradient = {
  type: 'gradient',
  gradientType: 'linear',
  enabled: true,
  rotation: 180,
  size: { height: 1 },
  colors: [
    { color: '#3B82F6', position: 0 },
    { color: '#1D4ED8', position: 1 },
  ],
};

function buildHeader(idPrefix, titleText, rightNode) {
  return {
    type: 'frame',
    id: `${idPrefix}_header`,
    name: 'header',
    width: 'fill_container',
    height: 44,
    layout: 'horizontal',
    justifyContent: 'space_between',
    alignItems: 'center',
    children: [
      {
        type: 'frame',
        id: `${idPrefix}_left`,
        name: 'left',
        layout: 'horizontal',
        gap: 10,
        alignItems: 'center',
        children: [
          icon(`${idPrefix}_back`, 'backIcon', 'arrow_back_ios_new', '#0F172A'),
          text(`${idPrefix}_title`, 'title', titleText, '#0F172A', 20, '700'),
        ],
      },
      rightNode,
    ],
  };
}

function buildTripCard(idPrefix, trip) {
  const children = [
    {
      type: 'frame',
      id: `${idPrefix}_top`,
      name: 'tripTop',
      width: 'fill_container',
      layout: 'horizontal',
      justifyContent: 'space_between',
      alignItems: 'center',
      children: [
        {
          type: 'frame',
          id: `${idPrefix}_info`,
          name: 'tripInfo',
          layout: 'vertical',
          gap: 4,
          children: [
            text(`${idPrefix}_name`, 'tripName', trip.name, '#0F172A', 16, '700'),
            text(`${idPrefix}_date`, 'tripDate', trip.date, '#64748B', 12, '500'),
          ],
        },
        pill(
          `${idPrefix}_status`,
          'tripStatus',
          trip.status,
          trip.statusBg,
          trip.statusText,
          trip.statusStroke,
        ),
      ],
    },
    text(`${idPrefix}_route`, 'tripRoute', trip.route, '#334155', 13, '500'),
  ];

  if (trip.progressWidth) {
    children.push({
      type: 'frame',
      id: `${idPrefix}_progress`,
      name: 'progressWrap',
      width: 'fill_container',
      layout: 'vertical',
      gap: 6,
      children: [
        progressTrack(`${idPrefix}_track`, trip.progressWidth, trip.progressColor),
        {
          type: 'frame',
          id: `${idPrefix}_footer`,
          name: 'tripFooter',
          width: 'fill_container',
          layout: 'horizontal',
          justifyContent: 'space_between',
          alignItems: 'center',
          children: [
            mono(`${idPrefix}_budget`, 'budgetText', trip.budget, '#0F172A', 12, '700'),
            (trip.progressLabel.endsWith('%')
              ? mono
              : text)(
              `${idPrefix}_label`,
              'progressText',
              trip.progressLabel,
              '#64748B',
              12,
              trip.progressLabel.endsWith('%') ? '700' : '600',
            ),
          ],
        },
      ],
    });
  } else {
    children.push({
      type: 'frame',
      id: `${idPrefix}_footer`,
      name: 'tripFooter',
      width: 'fill_container',
      layout: 'horizontal',
      justifyContent: 'space_between',
      alignItems: 'center',
      children: [
        mono(`${idPrefix}_budget`, 'budgetText', trip.budget, '#0F172A', 12, '700'),
        text(`${idPrefix}_label`, 'progressText', trip.progressLabel, '#64748B', 12, '600'),
      ],
    });
  }

  if (trip.actions) {
    children.push({
      type: 'frame',
      id: `${idPrefix}_actions`,
      name: 'actions',
      width: 'fill_container',
      layout: 'horizontal',
      gap: 8,
      children: trip.actions.map((label, idx) => ({
        type: 'frame',
        id: `${idPrefix}_action_${idx}`,
        name: label,
        width: 'fill_container',
        height: 34,
        fill: '#F8FAFC',
        cornerRadius: 12,
        stroke: '#E2E8F0',
        strokeWidth: 1,
        strokeAlignment: 'inner',
        layout: 'vertical',
        justifyContent: 'center',
        alignItems: 'center',
        children: [
          text(
            `${idPrefix}_action_text_${idx}`,
            'actionLabel',
            label,
            '#334155',
            12,
            '600',
          ),
        ],
      })),
    });
  }

  return {
    type: 'frame',
    id: idPrefix,
    name: trip.name,
    width: 'fill_container',
    fill: '#FFFFFF',
    cornerRadius: 20,
    stroke: '#E2E8F0',
    strokeWidth: 1,
    strokeAlignment: 'inner',
    effect: shadow,
    layout: 'vertical',
    gap: 10,
    padding: 14,
    children,
  };
}

function buildTravelHome() {
  return {
    type: 'frame',
    id: 'VuIjQ',
    x: baseX,
    y: 360,
    name: '旅行管理',
    clip: true,
    width: 393,
    height: 1136,
    fill: '#F8FAFC',
    layout: 'vertical',
    gap: 12,
    padding: screenPadding,
    children: [
      {
        type: 'frame',
        id: 'TrvHomeHeader',
        name: 'header',
        width: 'fill_container',
        height: 44,
        layout: 'horizontal',
        justifyContent: 'space_between',
        alignItems: 'center',
        children: [
          text('TrvHomeTitle', 'title', '旅行管理', '#0F172A', 24, '700'),
          {
            type: 'frame',
            id: 'TrvHomeAdd',
            name: 'addBtn',
            width: 40,
            height: 40,
            fill: '#1D4ED8',
            cornerRadius: 12,
            layout: 'vertical',
            justifyContent: 'center',
            alignItems: 'center',
            children: [text('TrvHomeAddText', 'addIcon', '+', '#FFFFFF', 24, '500')],
          },
        ],
      },
      {
        type: 'frame',
        id: 'TrvHomeHero',
        name: 'hero',
        width: 'fill_container',
        fill: heroGradient,
        cornerRadius: 22,
        stroke: '#BFDBFE',
        strokeWidth: 1,
        strokeAlignment: 'inner',
        layout: 'vertical',
        gap: 10,
        padding: 16,
        children: [
          text('TrvHomeHeroTitle', 'heroTitle', '今年已规划 3 次旅行', '#0F172A', 24, '700'),
          text(
            'TrvHomeHeroDesc',
            'heroDesc',
            '把线路、预算和同行人集中管理，出发前就把节奏安排好。',
            '#475569',
            13,
            '500',
            { textGrowth: 'fixed-width', width: 'fill_container' },
          ),
          {
            type: 'frame',
            id: 'TrvHomeMetrics',
            name: 'metrics',
            width: 'fill_container',
            layout: 'horizontal',
            gap: 10,
            children: [
              {
                type: 'frame',
                id: 'TrvHomeMetric1',
                name: '进行中',
                width: 'fill_container',
                fill: '#FFFFFFAA',
                cornerRadius: 16,
                stroke: '#DBEAFE',
                strokeWidth: 1,
                strokeAlignment: 'inner',
                layout: 'vertical',
                gap: 4,
                padding: 12,
                children: [
                  text('TrvHomeMetric1L', 'metricLabel', '进行中', '#64748B', 11, '600'),
                  mono('TrvHomeMetric1V', 'metricValue', '1', '#0F172A', 22, '700'),
                ],
              },
              {
                type: 'frame',
                id: 'TrvHomeMetric2',
                name: '总预算',
                width: 'fill_container',
                fill: '#FFFFFFAA',
                cornerRadius: 16,
                stroke: '#DBEAFE',
                strokeWidth: 1,
                strokeAlignment: 'inner',
                layout: 'vertical',
                gap: 4,
                padding: 12,
                children: [
                  text('TrvHomeMetric2L', 'metricLabel', '总预算', '#64748B', 11, '600'),
                  mono('TrvHomeMetric2V', 'metricValue', '¥18,000', '#0F172A', 22, '700'),
                ],
              },
              {
                type: 'frame',
                id: 'TrvHomeMetric3',
                name: '已花费',
                width: 'fill_container',
                fill: '#FFFFFFAA',
                cornerRadius: 16,
                stroke: '#DBEAFE',
                strokeWidth: 1,
                strokeAlignment: 'inner',
                layout: 'vertical',
                gap: 4,
                padding: 12,
                children: [
                  text('TrvHomeMetric3L', 'metricLabel', '已花费', '#64748B', 11, '600'),
                  mono('TrvHomeMetric3V', 'metricValue', '¥6,480', '#0F172A', 22, '700'),
                ],
              },
            ],
          },
        ],
      },
      {
        type: 'frame',
        id: 'TrvHomeTabs',
        name: 'filterTabs',
        width: 'fill_container',
        height: 42,
        fill: '#FFFFFF',
        cornerRadius: 16,
        stroke: '#DDE3EE',
        strokeWidth: 1,
        strokeAlignment: 'inner',
        layout: 'horizontal',
        gap: 6,
        padding: 4,
        children: [
          {
            type: 'frame',
            id: 'TrvHomeTab1',
            name: '全部',
            width: 'fill_container',
            height: 34,
            fill: '#1D4ED8',
            cornerRadius: 12,
            layout: 'vertical',
            justifyContent: 'center',
            alignItems: 'center',
            children: [text('TrvHomeTab1T', 'tabLabel', '全部', '#FFFFFF', 12, '700')],
          },
          {
            type: 'frame',
            id: 'TrvHomeTab2',
            name: '进行中',
            width: 'fill_container',
            height: 34,
            fill: '#FFFFFF',
            cornerRadius: 12,
            layout: 'vertical',
            justifyContent: 'center',
            alignItems: 'center',
            children: [text('TrvHomeTab2T', 'tabLabel', '进行中', '#64748B', 12, '600')],
          },
          {
            type: 'frame',
            id: 'TrvHomeTab3',
            name: '计划中',
            width: 'fill_container',
            height: 34,
            fill: '#FFFFFF',
            cornerRadius: 12,
            layout: 'vertical',
            justifyContent: 'center',
            alignItems: 'center',
            children: [text('TrvHomeTab3T', 'tabLabel', '计划中', '#64748B', 12, '600')],
          },
        ],
      },
      {
        type: 'frame',
        id: 'TrvHomeList',
        name: 'tripList',
        width: 'fill_container',
        layout: 'vertical',
        gap: 12,
        children: [
          buildTripCard('TrvTrip1', {
            name: '云南慢旅行',
            date: '2026.04.12 - 2026.04.20',
            route: '昆明 · 大理 · 丽江',
            status: '进行中',
            statusBg: '#EFF6FF',
            statusText: '#1D4ED8',
            statusStroke: '#BFDBFE',
            budget: '¥4,680 / ¥8,000',
            progressWidth: 216,
            progressColor: '#1D4ED8',
            progressLabel: '64%',
            actions: ['概览', '线路', '费用'],
          }),
          buildTripCard('TrvTrip2', {
            name: '日本关西赏樱',
            date: '2026.03.28 - 2026.04.05',
            route: '大阪 · 京都 · 奈良',
            status: '计划中',
            statusBg: '#FFF7ED',
            statusText: '#C2410C',
            statusStroke: '#FED7AA',
            budget: '¥0 / ¥12,000',
            progressLabel: '等待安排行程',
          }),
          buildTripCard('TrvTrip3', {
            name: '周末海岛短途',
            date: '2026.05.02 - 2026.05.04',
            route: '三亚',
            status: '已完成',
            statusBg: '#ECFDF5',
            statusText: '#15803D',
            statusStroke: '#BBF7D0',
            budget: '¥1,800 / ¥3,200',
            progressLabel: '已归档',
          }),
        ],
      },
      {
        type: 'frame',
        id: 'TrvHomeFabWrap',
        name: 'fabWrap',
        width: 'fill_container',
        height: 84,
        layout: 'vertical',
        alignItems: 'end',
        children: [
          {
            type: 'frame',
            id: 'TrvHomeFab',
            name: 'fab',
            width: 58,
            height: 58,
            fill: fabGradient,
            cornerRadius: 20,
            effect: {
              type: 'shadow',
              shadowType: 'outer',
              color: '#1D4ED833',
              offset: { x: 0, y: 10 },
              blur: 24,
            },
            layout: 'vertical',
            justifyContent: 'center',
            alignItems: 'center',
            children: [text('TrvHomeFabText', 'fabIcon', '+', '#FFFFFF', 28, '500')],
          },
        ],
      },
    ],
  };
}

function buildTravelNew() {
  return {
    type: 'frame',
    id: 'TrvNew01',
    x: baseX + gapX,
    y: 360,
    name: '旅行管理-新增旅行',
    clip: true,
    width: 393,
    height: 1180,
    fill: '#F8FAFC',
    layout: 'vertical',
    gap: 12,
    padding: screenPadding,
    children: [
      buildHeader('TrvNewHeader', '新增旅行', text('TrvNewDraft', 'draftText', '草稿', '#64748B', 12, '600')),
      {
        type: 'frame',
        id: 'TrvNewIntro',
        name: 'introCard',
        width: 'fill_container',
        layout: 'vertical',
        gap: 8,
        padding: 16,
        cornerRadius: 20,
        fill: {
          type: 'gradient',
          gradientType: 'linear',
          enabled: true,
          rotation: 135,
          size: { height: 1 },
          colors: [
            { color: '#FFF7ED', position: 0 },
            { color: '#FFE7D1', position: 1 },
          ],
        },
        stroke: '#FED7AA',
        strokeWidth: 1,
        strokeAlignment: 'inner',
        children: [
          text('TrvNewIntroTitle', 'introTitle', '先把这次旅行的关键条件定下来', '#7C2D12', 18, '700'),
          text(
            'TrvNewIntroDesc',
            'introDesc',
            '填写名称、时间、目的地和预算后，就可以继续做线路和费用规划。',
            '#9A3412',
            13,
            '500',
            { textGrowth: 'fixed-width', width: 'fill_container' },
          ),
        ],
      },
      {
        type: 'frame',
        id: 'TrvNewForm',
        name: 'form',
        width: 'fill_container',
        layout: 'vertical',
        gap: 12,
        children: [
          field('TrvNewField1', '旅行名称', '日本关西赏樱'),
          field('TrvNewField2', '旅行日期', '2026.03.28 - 2026.04.05'),
          field('TrvNewField3', '目的地', '大阪 / 京都 / 奈良'),
          {
            type: 'frame',
            id: 'TrvNewRow',
            name: '同行人和预算',
            width: 'fill_container',
            layout: 'horizontal',
            gap: 10,
            children: [
              field('TrvNewField4', '同行人', '2 人'),
              field('TrvNewField5', '预估预算', '¥12,000', { mono: true }),
            ],
          },
          {
            type: 'frame',
            id: 'TrvNewStyle',
            name: '旅行风格',
            width: 'fill_container',
            layout: 'vertical',
            gap: 6,
            children: [
              text('TrvNewStyleLabel', 'label', '旅行风格', '#334155', 13, '600'),
              {
                type: 'frame',
                id: 'TrvNewStyleChips',
                name: 'chips',
                width: 'fill_container',
                layout: 'horizontal',
                gap: 8,
                children: [
                  chip('TrvNewStyle1', '赏樱', '赏樱', '#EFF6FF', '#1D4ED8', '#BFDBFE'),
                  chip('TrvNewStyle2', '城市散步', '城市散步', '#F8FAFC', '#334155', '#E2E8F0'),
                  chip('TrvNewStyle3', '拍照', '拍照', '#F8FAFC', '#334155', '#E2E8F0'),
                ],
              },
            ],
          },
          field(
            'TrvNewField6',
            '备注',
            '想把樱花、古寺和夜景安排在前 5 天，最后留 1 天购物和机动时间。',
            { multiline: true, height: 132, placeholder: true },
          ),
        ],
      },
      {
        type: 'frame',
        id: 'TrvNewCtas',
        name: 'ctaGroup',
        width: 'fill_container',
        layout: 'vertical',
        gap: 10,
        children: [
          actionButton('TrvNewBtn1', '创建旅行', true),
          actionButton('TrvNewBtn2', '保存为草稿', false),
        ],
      },
    ],
  };
}

function buildSegment(idPrefix, selected) {
  const items = ['概览', '线路', '费用'];
  return {
    type: 'frame',
    id: `${idPrefix}_wrap`,
    name: 'segment',
    width: 'fill_container',
    height: 42,
    fill: '#FFFFFF',
    cornerRadius: 16,
    stroke: '#DDE3EE',
    strokeWidth: 1,
    strokeAlignment: 'inner',
    layout: 'horizontal',
    gap: 6,
    padding: 4,
    children: items.map((item, idx) => ({
      type: 'frame',
      id: `${idPrefix}_${idx}`,
      name: item,
      width: 'fill_container',
      height: 34,
      fill: item === selected ? '#1D4ED8' : '#FFFFFF',
      cornerRadius: 12,
      layout: 'vertical',
      justifyContent: 'center',
      alignItems: 'center',
      children: [
        text(
          `${idPrefix}_${idx}_text`,
          'tabLabel',
          item,
          item === selected ? '#FFFFFF' : '#64748B',
          12,
          item === selected ? '700' : '600',
        ),
      ],
    })),
  };
}

function buildTravelOverview() {
  return {
    type: 'frame',
    id: 'TrvOvr01',
    x: baseX + gapX * 2,
    y: 360,
    name: '旅行管理-概览',
    clip: true,
    width: 393,
    height: 1268,
    fill: '#F8FAFC',
    layout: 'vertical',
    gap: 12,
    padding: screenPadding,
    children: [
      buildHeader(
        'TrvOvrHeader',
        '云南慢旅行',
        pill('TrvOvrStatus', 'status', '进行中', '#EFF6FF', '#1D4ED8', '#BFDBFE'),
      ),
      {
        type: 'frame',
        id: 'TrvOvrSummary',
        name: 'summaryCard',
        width: 'fill_container',
        layout: 'vertical',
        gap: 10,
        padding: 16,
        cornerRadius: 22,
        fill: heroGradient,
        stroke: '#BFDBFE',
        strokeWidth: 1,
        strokeAlignment: 'inner',
        children: [
          text('TrvOvrRoute', 'routeText', '昆明 · 大理 · 丽江', '#0F172A', 24, '700'),
          text(
            'TrvOvrDate',
            'dateText',
            '2026.04.12 - 2026.04.20 · 9 天 8 晚 · 2 人同行',
            '#475569',
            13,
            '500',
            { textGrowth: 'fixed-width', width: 'fill_container' },
          ),
          {
            type: 'frame',
            id: 'TrvOvrMetrics',
            name: 'stats',
            width: 'fill_container',
            layout: 'horizontal',
            gap: 10,
            children: [
              {
                type: 'frame',
                id: 'TrvOvrMetric1',
                name: '行程完成度',
                width: 'fill_container',
                layout: 'vertical',
                gap: 4,
                padding: 12,
                cornerRadius: 16,
                fill: '#FFFFFFAA',
                children: [
                  text('TrvOvrMetric1L', 'label', '行程完成度', '#64748B', 11, '600'),
                  mono('TrvOvrMetric1V', 'value', '64%', '#0F172A', 22, '700'),
                ],
              },
              {
                type: 'frame',
                id: 'TrvOvrMetric2',
                name: '已预订',
                width: 'fill_container',
                layout: 'vertical',
                gap: 4,
                padding: 12,
                cornerRadius: 16,
                fill: '#FFFFFFAA',
                children: [
                  text('TrvOvrMetric2L', 'label', '已预订', '#64748B', 11, '600'),
                  mono('TrvOvrMetric2V', 'value', '5 项', '#0F172A', 22, '700'),
                ],
              },
              {
                type: 'frame',
                id: 'TrvOvrMetric3',
                name: '剩余预算',
                width: 'fill_container',
                layout: 'vertical',
                gap: 4,
                padding: 12,
                cornerRadius: 16,
                fill: '#FFFFFFAA',
                children: [
                  text('TrvOvrMetric3L', 'label', '剩余预算', '#64748B', 11, '600'),
                  mono('TrvOvrMetric3V', 'value', '¥3,320', '#0F172A', 22, '700'),
                ],
              },
            ],
          },
        ],
      },
      buildSegment('TrvOvrSeg', '概览'),
      {
        type: 'frame',
        id: 'TrvOvrTiles',
        name: 'overviewTiles',
        width: 'fill_container',
        layout: 'horizontal',
        gap: 10,
        children: [
          {
            type: 'frame',
            id: 'TrvOvrTile1',
            name: '交通',
            width: 'fill_container',
            layout: 'vertical',
            gap: 6,
            padding: 14,
            cornerRadius: 18,
            fill: '#FFFFFF',
            stroke: '#E2E8F0',
            strokeWidth: 1,
            strokeAlignment: 'inner',
            children: [
              text('TrvOvrTile1L', 'label', '交通', '#64748B', 12, '600'),
              mono('TrvOvrTile1V', 'value', '3 段', '#0F172A', 20, '700'),
              text('TrvOvrTile1D', 'desc', '高铁 + 包车', '#475569', 12, '500'),
            ],
          },
          {
            type: 'frame',
            id: 'TrvOvrTile2',
            name: '住宿',
            width: 'fill_container',
            layout: 'vertical',
            gap: 6,
            padding: 14,
            cornerRadius: 18,
            fill: '#FFFFFF',
            stroke: '#E2E8F0',
            strokeWidth: 1,
            strokeAlignment: 'inner',
            children: [
              text('TrvOvrTile2L', 'label', '住宿', '#64748B', 12, '600'),
              mono('TrvOvrTile2V', 'value', '4 晚', '#0F172A', 20, '700'),
              text('TrvOvrTile2D', 'desc', '古城民宿为主', '#475569', 12, '500'),
            ],
          },
        ],
      },
      {
        type: 'frame',
        id: 'TrvOvrStage',
        name: 'stageCard',
        width: 'fill_container',
        layout: 'vertical',
        gap: 12,
        padding: 14,
        cornerRadius: 20,
        fill: '#FFFFFF',
        stroke: '#E2E8F0',
        strokeWidth: 1,
        strokeAlignment: 'inner',
        children: [
          text('TrvOvrStageTitle', 'sectionTitle', '本次旅行阶段', '#0F172A', 16, '700'),
          {
            type: 'frame',
            id: 'TrvOvrStageRow',
            name: 'stageRow',
            width: 'fill_container',
            layout: 'horizontal',
            gap: 10,
            children: [
              { type: 'frame', id: 'TrvOvrStage1', name: '准备', width: 'fill_container', layout: 'vertical', gap: 4, padding: 12, cornerRadius: 16, fill: '#F8FAFC', children: [text('TrvOvrStage1L', 'stageLabel', '准备', '#64748B', 12, '600'), text('TrvOvrStage1V', 'stageValue', '已完成', '#0F172A', 14, '700')] },
              { type: 'frame', id: 'TrvOvrStage2', name: '执行', width: 'fill_container', layout: 'vertical', gap: 4, padding: 12, cornerRadius: 16, fill: '#EFF6FF', children: [text('TrvOvrStage2L', 'stageLabel', '执行', '#1D4ED8', 12, '700'), text('TrvOvrStage2V', 'stageValue', '进行中', '#0F172A', 14, '700')] },
              { type: 'frame', id: 'TrvOvrStage3', name: '回顾', width: 'fill_container', layout: 'vertical', gap: 4, padding: 12, cornerRadius: 16, fill: '#F8FAFC', children: [text('TrvOvrStage3L', 'stageLabel', '回顾', '#64748B', 12, '600'), text('TrvOvrStage3V', 'stageValue', '待开始', '#0F172A', 14, '700')] },
            ],
          },
        ],
      },
      {
        type: 'frame',
        id: 'TrvOvrBudget',
        name: 'budgetCard',
        width: 'fill_container',
        layout: 'vertical',
        gap: 12,
        padding: 14,
        cornerRadius: 20,
        fill: '#FFFFFF',
        stroke: '#E2E8F0',
        strokeWidth: 1,
        strokeAlignment: 'inner',
        children: [
          {
            type: 'frame',
            id: 'TrvOvrBudgetHead',
            name: 'budgetHead',
            width: 'fill_container',
            layout: 'horizontal',
            justifyContent: 'space_between',
            alignItems: 'center',
            children: [
              text('TrvOvrBudgetTitle', 'sectionTitle', '预算概览', '#0F172A', 16, '700'),
              text('TrvOvrBudgetMeta', 'sectionMeta', '已用 58%', '#64748B', 12, '600'),
            ],
          },
          progressTrack('TrvOvrBudgetTrack', 204, '#1D4ED8'),
          {
            type: 'frame',
            id: 'TrvOvrBudgetRows',
            name: 'budgetRows',
            width: 'fill_container',
            layout: 'vertical',
            gap: 10,
            children: [
              ['住宿', '¥2,100 / ¥3,000'],
              ['交通', '¥2,480 / ¥3,200'],
              ['餐饮', '¥1,100 / ¥1,800'],
            ].map((row, idx) => ({
              type: 'frame',
              id: `TrvOvrBudgetRow${idx}`,
              name: `row${idx + 1}`,
              width: 'fill_container',
              layout: 'horizontal',
              justifyContent: 'space_between',
              children: [
                text(`TrvOvrBudgetRowL${idx}`, 'rowLabel', row[0], '#475569', 13, '500'),
                mono(`TrvOvrBudgetRowV${idx}`, 'rowValue', row[1], '#0F172A', 12, '700'),
              ],
            })),
          },
        ],
      },
      actionButton('TrvOvrContinue', '继续规划线路', true),
    ],
  };
}

function buildTravelRoute() {
  const days = [
    {
      title: 'Day 1 · 昆明适应日',
      pace: '轻松',
      paceBg: '#F8FAFC',
      paceColor: '#475569',
      stops: [
        '09:30 抵达昆明长水机场，前往酒店办理入住',
        '15:00 翠湖公园散步，傍晚去南屏街吃菌锅',
      ],
    },
    {
      title: 'Day 2 · 前往大理',
      pace: '移动日',
      paceBg: '#EFF6FF',
      paceColor: '#1D4ED8',
      stops: [
        '08:40 高铁出发，约中午抵达大理站',
        '14:30 古城入住，傍晚逛人民路和龙龛码头',
      ],
    },
    {
      title: 'Day 3 · 洱海环线',
      pace: '重头戏',
      paceBg: '#ECFDF5',
      paceColor: '#15803D',
      stops: [
        '09:00 包车出发，双廊 - 喜洲 - 廊桥拍照路线',
        '17:30 返回古城，晚餐后整理第二天素材与购物清单',
      ],
    },
  ];

  return {
    type: 'frame',
    id: 'TrvRte01',
    x: baseX + gapX * 3,
    y: 360,
    name: '旅行管理-线路规划',
    clip: true,
    width: 393,
    height: 1368,
    fill: '#F8FAFC',
    layout: 'vertical',
    gap: 12,
    padding: screenPadding,
    children: [
      buildHeader('TrvRteHeader', '线路规划', text('TrvRteMeta', 'meta', '9 天 8 晚', '#64748B', 12, '600')),
      {
        type: 'frame',
        id: 'TrvRteHero',
        name: 'tripCard',
        width: 'fill_container',
        layout: 'vertical',
        gap: 10,
        padding: 16,
        cornerRadius: 22,
        fill: heroGradient,
        stroke: '#BFDBFE',
        strokeWidth: 1,
        strokeAlignment: 'inner',
        children: [
          text('TrvRteHeroTitle', 'title', '先把城市切换和节奏定下来', '#0F172A', 20, '700'),
          text(
            'TrvRteHeroDesc',
            'desc',
            '当前规划以“昆明适应 - 大理慢节奏 - 丽江收尾”为主，避免高频换酒店。',
            '#475569',
            13,
            '500',
            { textGrowth: 'fixed-width', width: 'fill_container' },
          ),
          {
            type: 'frame',
            id: 'TrvRteHeroChips',
            name: 'chips',
            width: 'fill_container',
            layout: 'horizontal',
            gap: 8,
            children: [
              chip('TrvRteChip1', '昆明 1 晚', '昆明 1 晚', '#FFFFFFAA', '#0F172A'),
              chip('TrvRteChip2', '大理 3 晚', '大理 3 晚', '#FFFFFFAA', '#0F172A'),
              chip('TrvRteChip3', '丽江 4 晚', '丽江 4 晚', '#FFFFFFAA', '#0F172A'),
            ],
          },
        ],
      },
      buildSegment('TrvRteSeg', '线路'),
      {
        type: 'frame',
        id: 'TrvRteList',
        name: 'dayList',
        width: 'fill_container',
        layout: 'vertical',
        gap: 12,
        children: days.map((day, idx) => ({
          type: 'frame',
          id: `TrvRteDay${idx}`,
          name: `Day ${idx + 1}`,
          width: 'fill_container',
          layout: 'vertical',
          gap: 10,
          padding: 14,
          cornerRadius: 20,
          fill: '#FFFFFF',
          stroke: '#E2E8F0',
          strokeWidth: 1,
          strokeAlignment: 'inner',
          children: [
            {
              type: 'frame',
              id: `TrvRteDayHead${idx}`,
              name: 'dayHead',
              width: 'fill_container',
              layout: 'horizontal',
              justifyContent: 'space_between',
              alignItems: 'center',
              children: [
                text(`TrvRteDayTitle${idx}`, 'dayTitle', day.title, '#0F172A', 16, '700'),
                {
                  type: 'frame',
                  id: `TrvRtePace${idx}`,
                  name: 'pace',
                  height: 28,
                  fill: day.paceBg,
                  cornerRadius: 999,
                  layout: 'horizontal',
                  alignItems: 'center',
                  padding: [0, 12],
                  children: [
                    text(`TrvRtePaceText${idx}`, 'paceLabel', day.pace, day.paceColor, 12, '700'),
                  ],
                },
              ],
            },
            {
              type: 'frame',
              id: `TrvRteStops${idx}`,
              name: 'stops',
              width: 'fill_container',
              layout: 'vertical',
              gap: 8,
              children: day.stops.map((line, lineIdx) =>
                text(
                  `TrvRteStop${idx}${lineIdx}`,
                  `stop${lineIdx + 1}`,
                  line,
                  '#334155',
                  13,
                  '500',
                  { textGrowth: 'fixed-width', width: 'fill_container' },
                ),
              ),
            },
          ],
        })),
      },
      {
        type: 'frame',
        id: 'TrvRteTips',
        name: 'tipsCard',
        width: 'fill_container',
        layout: 'vertical',
        gap: 10,
        padding: 14,
        cornerRadius: 20,
        fill: '#FFFFFF',
        stroke: '#E2E8F0',
        strokeWidth: 1,
        strokeAlignment: 'inner',
        children: [
          text('TrvRteTipsTitle', 'sectionTitle', '规划提醒', '#0F172A', 16, '700'),
          text(
            'TrvRteTip1',
            'tip1',
            '1. 大理和丽江之间建议预留半天机动时间，避免行李转移过于仓促。',
            '#475569',
            13,
            '500',
            { textGrowth: 'fixed-width', width: 'fill_container' },
          ),
          text(
            'TrvRteTip2',
            'tip2',
            '2. 把最想去的拍照点集中在天气最稳定的 2 天，提升行程容错率。',
            '#475569',
            13,
            '500',
            { textGrowth: 'fixed-width', width: 'fill_container' },
          ),
        ],
      },
      actionButton('TrvRteBtn', '新增一天行程', true),
    ],
  };
}

function buildTravelExpense() {
  const categories = [
    ['住宿', '¥2,100 / ¥3,000', 228, '#1D4ED8'],
    ['交通', '¥1,680 / ¥2,400', 196, '#2563EB'],
    ['餐饮', '¥620 / ¥1,000', 160, '#60A5FA'],
    ['拍照和门票', '¥280 / ¥600', 126, '#93C5FD'],
  ];
  const expenses = [
    ['大理古城民宿', '住宿 · 04.13 · 双人分摊', '¥860'],
    ['昆明到大理高铁', '交通 · 04.13 · 双人', '¥524'],
    ['洱海环线包车订金', '交通 · 04.14 · 已支付', '¥300'],
  ];

  return {
    type: 'frame',
    id: 'TrvExp01',
    x: baseX + gapX * 4,
    y: 360,
    name: '旅行管理-费用管理',
    clip: true,
    width: 393,
    height: 1314,
    fill: '#F8FAFC',
    layout: 'vertical',
    gap: 12,
    padding: screenPadding,
    children: [
      buildHeader('TrvExpHeader', '费用管理', chip('TrvExpTag', 'monthTag', '当前旅程', '#F8FAFC', '#475569', '#E2E8F0')),
      {
        type: 'frame',
        id: 'TrvExpSummary',
        name: 'summaryCard',
        width: 'fill_container',
        layout: 'vertical',
        gap: 10,
        padding: 16,
        cornerRadius: 22,
        fill: heroGradient,
        stroke: '#BFDBFE',
        strokeWidth: 1,
        strokeAlignment: 'inner',
        children: [
          text('TrvExpSummaryTitle', 'summaryTitle', '旅程预算控制在健康区间', '#0F172A', 20, '700'),
          {
            type: 'frame',
            id: 'TrvExpSummaryStats',
            name: 'stats',
            width: 'fill_container',
            layout: 'horizontal',
            gap: 10,
            children: [
              { type: 'frame', id: 'TrvExpStat1', name: '总预算', width: 'fill_container', layout: 'vertical', gap: 4, padding: 12, cornerRadius: 16, fill: '#FFFFFFAA', children: [text('TrvExpStat1L', 'label', '总预算', '#64748B', 11, '600'), mono('TrvExpStat1V', 'value', '¥8,000', '#0F172A', 22, '700')] },
              { type: 'frame', id: 'TrvExpStat2', name: '已花费', width: 'fill_container', layout: 'vertical', gap: 4, padding: 12, cornerRadius: 16, fill: '#FFFFFFAA', children: [text('TrvExpStat2L', 'label', '已花费', '#64748B', 11, '600'), mono('TrvExpStat2V', 'value', '¥4,680', '#0F172A', 22, '700')] },
              { type: 'frame', id: 'TrvExpStat3', name: '剩余', width: 'fill_container', layout: 'vertical', gap: 4, padding: 12, cornerRadius: 16, fill: '#FFFFFFAA', children: [text('TrvExpStat3L', 'label', '剩余', '#64748B', 11, '600'), mono('TrvExpStat3V', 'value', '¥3,320', '#0F172A', 22, '700')] },
            ],
          },
        ],
      },
      buildSegment('TrvExpSeg', '费用'),
      {
        type: 'frame',
        id: 'TrvExpCategoryCard',
        name: 'categoryCard',
        width: 'fill_container',
        layout: 'vertical',
        gap: 12,
        padding: 14,
        cornerRadius: 20,
        fill: '#FFFFFF',
        stroke: '#E2E8F0',
        strokeWidth: 1,
        strokeAlignment: 'inner',
        children: [
          text('TrvExpCategoryTitle', 'sectionTitle', '分类预算', '#0F172A', 16, '700'),
          {
            type: 'frame',
            id: 'TrvExpCategoryRows',
            name: 'rows',
            width: 'fill_container',
            layout: 'vertical',
            gap: 10,
            children: categories.map((row, idx) => ({
              type: 'frame',
              id: `TrvExpCategory${idx}`,
              name: row[0],
              width: 'fill_container',
              layout: 'vertical',
              gap: 6,
              children: [
                {
                  type: 'frame',
                  id: `TrvExpCategoryHead${idx}`,
                  name: 'rowHead',
                  width: 'fill_container',
                  layout: 'horizontal',
                  justifyContent: 'space_between',
                  children: [
                    text(`TrvExpCategoryLabel${idx}`, 'label', row[0], '#475569', 13, '500'),
                    mono(`TrvExpCategoryValue${idx}`, 'value', row[1], '#0F172A', 12, '700'),
                  ],
                },
                progressTrack(`TrvExpCategoryTrack${idx}`, row[2], row[3]),
              ],
            })),
          },
        ],
      },
      {
        type: 'frame',
        id: 'TrvExpListCard',
        name: 'expenseListCard',
        width: 'fill_container',
        layout: 'vertical',
        gap: 12,
        padding: 14,
        cornerRadius: 20,
        fill: '#FFFFFF',
        stroke: '#E2E8F0',
        strokeWidth: 1,
        strokeAlignment: 'inner',
        children: [
          {
            type: 'frame',
            id: 'TrvExpListHead',
            name: 'head',
            width: 'fill_container',
            layout: 'horizontal',
            justifyContent: 'space_between',
            children: [
              text('TrvExpListTitle', 'sectionTitle', '最近支出', '#0F172A', 16, '700'),
              text('TrvExpListMeta', 'sectionMeta', '共 12 笔', '#64748B', 12, '600'),
            ],
          },
          {
            type: 'frame',
            id: 'TrvExpListRows',
            name: 'rows',
            width: 'fill_container',
            layout: 'vertical',
            gap: 10,
            children: expenses.map((row, idx) => ({
              type: 'frame',
              id: `TrvExpRow${idx}`,
              name: `expense${idx + 1}`,
              width: 'fill_container',
              layout: 'horizontal',
              justifyContent: 'space_between',
              alignItems: 'center',
              children: [
                {
                  type: 'frame',
                  id: `TrvExpRowLeft${idx}`,
                  name: 'left',
                  layout: 'vertical',
                  gap: 4,
                  children: [
                    text(`TrvExpRowTitle${idx}`, 'title', row[0], '#0F172A', 14, '600'),
                    text(`TrvExpRowMeta${idx}`, 'meta', row[1], '#64748B', 12, '500'),
                  ],
                },
                mono(`TrvExpRowAmount${idx}`, 'amount', row[2], '#0F172A', 14, '700'),
              ],
            })),
          },
        ],
      },
      {
        type: 'frame',
        id: 'TrvExpSettlement',
        name: 'settlementCard',
        width: 'fill_container',
        layout: 'vertical',
        gap: 8,
        padding: 14,
        cornerRadius: 20,
        fill: '#FFFFFF',
        stroke: '#E2E8F0',
        strokeWidth: 1,
        strokeAlignment: 'inner',
        children: [
          text('TrvExpSettlementTitle', 'title', '同行结算', '#0F172A', 16, '700'),
          text(
            'TrvExpSettlementDesc',
            'desc',
            '当前由你代付 ¥2,380，对方应分摊 ¥1,190。适合在旅程结束前统一结算。',
            '#475569',
            13,
            '500',
            { textGrowth: 'fixed-width', width: 'fill_container' },
          ),
        ],
      },
      actionButton('TrvExpBtn', '新增费用', true),
    ],
  };
}

data.children.push(
  buildTravelHome(),
  buildTravelNew(),
  buildTravelOverview(),
  buildTravelRoute(),
  buildTravelExpense(),
);

fs.writeFileSync(path, JSON.stringify(data, null, 2) + '\n');
console.log(`updated ${data.children.length} root nodes`);
